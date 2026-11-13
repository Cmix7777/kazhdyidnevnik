package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import io.github.cmix7777.kazhdyidnevnik.data.VoicePcm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Голосовое в текст средствами самого телефона (распознавание речи Google), без своих серверов и ключей.
 *
 * Запись раскодируется в звук 16 кГц и отдаётся распознаванию через трубу (так умеет Android 13 и новее).
 * Если служба распознавания трубу не читает, а слушает микрофон, — это видно по тому, что звук в трубу
 * так и не ушёл; такой текст выбрасывается, а человек видит, что телефон этого не умеет.
 * Готовый текст хранится на телефоне и второй раз не распознаётся.
 */
object VoiceText {
    sealed interface State {
        data class Working(val partial: String = "") : State
        data class Done(val text: String) : State
        data class Failed(val why: String) : State
    }

    private val mutable = MutableStateFlow<Map<String, State>>(emptyMap())
    val state: StateFlow<Map<String, State>> = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loaded = false

    /** Сколько звука труба держит, пока её не читают (64 КБ в Linux), с запасом. */
    private const val PIPE_SLACK = 72 * 1024
    private const val TAIL_SILENCE_MS = 3_000
    private const val KEEP = 400

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("voice-text", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context).all.mapNotNull { (k, v) -> (v as? String)?.let { k to State.Done(it) } }.toMap()
        if (saved.isNotEmpty()) mutable.update { saved + it }
    }

    /** Можно ли вообще пробовать на этом телефоне. */
    fun possible(): Boolean = Build.VERSION.SDK_INT >= 33

    private fun set(id: String, state: State) = mutable.update { it + (id to state) }

    fun hide(id: String) = mutable.update { it - id }

    /** Расшифровать голосовое [id] (его файл уже на телефоне). */
    fun start(context: Context, id: String) {
        val app = context.applicationContext
        load(app)
        if (mutable.value[id] is State.Working) return
        prefs(app).getString(id, null)?.let {
            set(id, State.Done(it))
            return
        }
        if (Build.VERSION.SDK_INT < 33) {
            set(id, State.Failed("расшифровка работает с Android 13"))
            return
        }
        val file = ChatFiles.file(app, id)
        if (!file.exists()) {
            set(id, State.Failed("голосовое ещё не скачалось"))
            return
        }
        set(id, State.Working())
        scope.launch {
            val pcm = withContext(Dispatchers.Default) { runCatching { decode(file) }.getOrNull() }
            if (pcm == null || pcm.size <= VoicePcm.RATE * 2 * TAIL_SILENCE_MS / 1000) {
                set(id, State.Failed("не получилось прочитать запись"))
                return@launch
            }
            val result = if (Build.VERSION.SDK_INT >= 33) {
                runCatching { recognize(app, id, pcm) }.getOrElse { State.Failed("распознавание сломалось, попробуй ещё раз") }
            } else {
                State.Failed("расшифровка работает с Android 13")
            }
            set(id, result)
            if (result is State.Done) {
                val p = prefs(app)
                val editor = p.edit()
                if (p.all.size >= KEEP) p.all.keys.take(KEEP / 4).forEach { editor.remove(it) }
                editor.putString(id, result.text).apply()
            }
        }
    }

    // ---------- звук ----------

    /** AAC из файла в моно 16 кГц, 16 бит, с тишиной в конце. */
    internal fun decode(file: File): ByteArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("в файле нет звука")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            val out = ShortBuilder()
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                val deadline = SystemClock.elapsedRealtime() + 60_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val buffer = codec.getInputBuffer(i)!!
                            val n = extractor.readSampleData(buffer, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    } else if (o >= 0) {
                        val buffer = codec.getOutputBuffer(o)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val ordered = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                            if (float) {
                                val fb = ordered.asFloatBuffer()
                                val chunk = FloatArray(fb.remaining()).also { fb.get(it) }
                                out.add(VoicePcm.fromFloat(chunk))
                            } else {
                                val sb = ordered.asShortBuffer()
                                out.add(ShortArray(sb.remaining()).also { sb.get(it) })
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return VoicePcm.toBytes(VoicePcm.toMono16k(out.build(), rate, channels), TAIL_SILENCE_MS)
        } finally {
            extractor.release()
        }
    }

    private class ShortBuilder {
        private var data = ShortArray(64 * 1024)
        private var size = 0
        fun add(chunk: ShortArray) {
            if (size + chunk.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + chunk.size))
            System.arraycopy(chunk, 0, data, size, chunk.size)
            size += chunk.size
        }
        fun build(): ShortArray = data.copyOf(size)
    }

    // ---------- распознавание ----------

    private class Session(val parts: List<String>, val written: Long, val total: Long, val error: Int?)

    @RequiresApi(33)
    private suspend fun recognize(context: Context, id: String, pcm: ByteArray): State {
        // Сначала распознавание на самом телефоне (без интернета), потом обычное.
        val kinds = buildList {
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) add(true)
            if (SpeechRecognizer.isRecognitionAvailable(context)) add(false)
        }
        if (kinds.isEmpty()) return State.Failed("на телефоне нет распознавания речи Google")
        var failure: State = State.Failed("этот телефон не умеет расшифровывать записанные голосовые")
        for (onDevice in kinds) {
            val parts = mutableListOf<String>()
            var offset = 0
            var deaf = false
            var error: Int? = null
            // Распознавание без «длинного сеанса» останавливается на первой паузе: тогда продолжаем с того места.
            for (round in 0 until 30) {
                val got = session(context, pcm, offset, onDevice) { partial ->
                    set(id, State.Working(VoicePcm.join(parts + partial)))
                }
                val readAll = got.written >= got.total
                val unread = !readAll && got.written <= PIPE_SLACK
                val plain = got.error == null || got.error == SpeechRecognizer.ERROR_NO_MATCH || got.error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (unread && !plain) {
                    // Служба сразу сказала, что не так (нет сети, нет языка): это и покажем.
                    error = got.error
                    break
                }
                if (unread) {
                    // Звук в трубу почти не ушёл: служба слушала не запись, а микрофон. Её текст не годится.
                    deaf = round == 0
                    error = null
                    break
                }
                parts += got.parts
                error = got.error
                if (readAll || got.error != null && got.parts.isEmpty()) break
                offset += (got.written - PIPE_SLACK / 2).toInt().coerceAtLeast(VoicePcm.RATE * 2)
                if (offset >= pcm.size - VoicePcm.RATE * 2 * TAIL_SILENCE_MS / 1000) break
            }
            val text = VoicePcm.join(parts)
            if (text.isNotEmpty()) return State.Done(text)
            if (deaf) continue
            when (error) {
                null, SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> return State.Failed("слов не разобрать")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                    failure = State.Failed("нужен интернет, попробуй ещё раз")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> failure = State.Failed("разреши приложению микрофон: без него Android не даёт расшифровывать")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    failure = State.Failed("русский язык для распознавания ещё не скачан на телефоне")
                    if (onDevice) runCatching { downloadRussian(context) }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> failure = State.Failed("распознавание занято, попробуй через минуту")
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> failure = State.Failed("слишком много попыток, попробуй позже")
                else -> failure = State.Failed("распознавание не получилось, попробуй ещё раз")
            }
        }
        return failure
    }

    @RequiresApi(33)
    private fun intent(context: Context, source: ParcelFileDescriptor?): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (source != null) {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, VoicePcm.RATE)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }
    }

    @RequiresApi(33)
    private fun downloadRussian(context: Context) {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try {
            recognizer.triggerModelDownload(intent(context, null))
        } finally {
            recognizer.destroy()
        }
    }

    /** Один сеанс распознавания звука с [offset]. Вызывается на главном потоке. */
    @RequiresApi(33)
    private suspend fun session(
        context: Context,
        pcm: ByteArray,
        offset: Int,
        onDevice: Boolean,
        onPartial: (String) -> Unit,
    ): Session {
        val total = (pcm.size - offset).toLong()
        val written = AtomicLong(0)
        val pipe = ParcelFileDescriptor.createPipe()
        val read = pipe[0]
        val write = pipe[1]
        val closed = AtomicBoolean(false)
        fun closeAll() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { write.close() }
            runCatching { read.close() }
        }
        val writer = Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { output ->
                    var pos = offset
                    while (pos < pcm.size) {
                        val n = minOf(4_096, pcm.size - pos)
                        output.write(pcm, pos, n)
                        pos += n
                        written.set((pos - offset).toLong())
                    }
                }
            } catch (e: Exception) {
                // Распознавание закрыло трубу: дальше писать некуда.
            }
        }.apply { isDaemon = true; name = "voice-text-writer" }

        val recognizer = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        val parts = mutableListOf<String>()
        val seconds = total / (VoicePcm.RATE * 2)
        val result = withTimeoutOrNull(30_000 + seconds * 2_000) {
            // 0 — сеанс кончился сам, иначе номер ошибки распознавания.
            suspendCancellableCoroutine<Int> { cont ->
                fun finish(code: Int) {
                    if (cont.isActive) cont.resume(code)
                }
                fun texts(bundle: Bundle?): String =
                    bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onPartialResults(partialResults: Bundle?) {
                        val t = texts(partialResults)
                        if (t.isNotBlank()) onPartial(t)
                    }
                    override fun onSegmentResults(segmentResults: Bundle) {
                        val t = texts(segmentResults)
                        if (t.isNotBlank()) parts += t
                    }
                    override fun onEndOfSegmentedSession() = finish(0)
                    override fun onResults(results: Bundle?) {
                        val t = texts(results)
                        if (t.isNotBlank()) parts += t
                        finish(0)
                    }
                    override fun onError(error: Int) = finish(error)
                })
                recognizer.startListening(intent(context, read))
                writer.start()
            }
        }
        // Дать писателю дописать то, что служба уже прочла, прежде чем закрыть трубу.
        delay(50)
        val error = when {
            result == null -> if (parts.isEmpty()) SpeechRecognizer.ERROR_SPEECH_TIMEOUT else null
            result == 0 -> null
            else -> result
        }
        runCatching { recognizer.cancel() }
        recognizer.destroy()
        val done = written.get()
        closeAll()
        return Session(parts.toList(), done, total, error)
    }
}
