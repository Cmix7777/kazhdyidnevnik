package io.github.cmix7777.kazhdyidnevnik.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.service.VoiceText
import io.github.cmix7777.kazhdyidnevnik.data.ChatCircle
import io.github.cmix7777.kazhdyidnevnik.data.ChatVoice
import io.github.cmix7777.kazhdyidnevnik.service.ChatFiles
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.sqrt

// ---------- голосовые ----------

/** Запись голосового: AAC 32 кбит/с, моно. Не длиннее трёх минут. */
class VoiceRecorder(private val context: Context) {
    class Result(val bytes: ByteArray, val durationMs: Long, val wave: String)

    private var recorder: MediaRecorder? = null
    private val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
    private val levels = ArrayList<Int>()
    var startedAt = 0L
        private set

    fun start(): Boolean = try {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(22_050)
        r.setAudioEncodingBitRate(32_000)
        r.setMaxDuration(MAX_MS.toInt())
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        startedAt = SystemClock.elapsedRealtime()
        true
    } catch (e: Exception) {
        release()
        false
    }

    /** Громкость прямо сейчас, 0..1 (для живой волны). */
    fun level(): Float {
        val raw = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        levels += raw
        return (sqrt(raw / 32767f)).coerceIn(0f, 1f)
    }

    val elapsed: Long get() = if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt

    fun stop(): Result? {
        val duration = elapsed
        val ok = runCatching { recorder?.stop() }.isSuccess
        release()
        if (!ok || duration < 700) {
            file.delete()
            return null
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
        file.delete()
        return bytes?.let { Result(it, duration, wave(levels)) }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        release()
        file.delete()
    }

    private fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }

    companion object {
        const val MAX_MS = 180_000L

        /** Волна голосового: 48 столбиков громкости цифрами 0..9. */
        fun wave(levels: List<Int>): String {
            if (levels.isEmpty()) return ""
            val bars = 48
            val top = max(1, levels.maxOrNull() ?: 1)
            return (0 until bars).joinToString("") { i ->
                val from = i * levels.size / bars
                val to = max(from + 1, (i + 1) * levels.size / bars)
                val peak = levels.subList(from, minOf(to, levels.size)).maxOrNull() ?: 0
                (sqrt(peak / top.toFloat()) * 9f).toInt().coerceIn(0, 9).toString()
            }
        }
    }
}

/**
 * Одно голосовое играет за раз. Скорость (1x, 1,5x, 2x) запоминается; недослушанное голосовое
 * продолжается с того места, где остановился (пока приложение открыто).
 */
internal object VoicePlayer {
    data class Playing(val id: String, val position: Long, val duration: Long, val paused: Boolean)

    private val mutable = MutableStateFlow<Playing?>(null)
    val state: StateFlow<Playing?> = mutable.asStateFlow()
    private val mutableSpeed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = mutableSpeed.asStateFlow()
    private var speedLoaded = false
    private val positions = HashMap<String, Long>()
    private var player: MediaPlayer? = null

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("voice", Context.MODE_PRIVATE)

    fun loadSpeed(context: Context) {
        if (speedLoaded) return
        speedLoaded = true
        mutableSpeed.value = prefs(context).getFloat("speed", 1f).takeIf { it in SPEEDS } ?: 1f
    }

    /** Следующая скорость по кругу: 1x → 1,5x → 2x → 1x. */
    fun nextSpeed(context: Context) {
        loadSpeed(context)
        val next = SPEEDS[(SPEEDS.indexOf(mutableSpeed.value) + 1) % SPEEDS.size]
        mutableSpeed.value = next
        prefs(context).edit().putFloat("speed", next).apply()
        player?.let { p -> if (runCatching { p.isPlaying }.getOrDefault(false)) applySpeed(p) }
    }

    /** Скорость меняют только у играющего плеера: у стоящего это на части телефонов запускает звук. */
    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(mutableSpeed.value) }
    }

    private val SPEEDS = listOf(1f, 1.5f, 2f)
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            val s = mutable.value ?: return
            mutable.value = s.copy(position = runCatching { p.currentPosition.toLong() }.getOrDefault(s.position))
            if (!s.paused) handler.postDelayed(this, 60)
        }
    }

    fun toggle(context: Context, id: String) {
        val current = mutable.value
        val p0 = player
        if (current != null && current.id == id && p0 != null) {
            if (p0.isPlaying) {
                p0.pause()
                mutable.value = current.copy(paused = true)
            } else {
                p0.start()
                applySpeed(p0)
                mutable.value = current.copy(paused = false)
                handler.post(ticker)
            }
            return
        }
        loadSpeed(context)
        stop()
        val file = ChatFiles.file(context, id)
        if (!file.exists()) return
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            p.setDataSource(file.absolutePath)
            p.prepare()
        } catch (e: Exception) {
            p.release()
            return
        }
        p.setOnCompletionListener {
            // Дослушал до конца — в следующий раз сначала.
            positions.remove(id)
            stop(remember = false)
        }
        player = p
        val resume = positions[id]?.takeIf { it in 1 until (p.duration - 700L).coerceAtLeast(1L) } ?: 0L
        if (resume > 0) runCatching { p.seekTo(resume.toInt()) }
        p.start()
        applySpeed(p)
        mutable.value = Playing(id, resume, p.duration.toLong(), false)
        handler.post(ticker)
    }

    fun stop(remember: Boolean = true) {
        if (remember) {
            val s = mutable.value
            val p = player
            if (s != null && p != null) positions[s.id] = runCatching { p.currentPosition.toLong() }.getOrDefault(s.position)
        }
        handler.removeCallbacks(ticker)
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        mutable.value = null
    }
}

internal fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Голосовое в ленте: кнопка, волна (пройденное ярче) и длина. */
@Composable
internal fun VoiceBubble(voice: ChatVoice, mine: Boolean, accent: Color, onReady: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by ChatFiles.version.collectAsState()
    val playing by VoicePlayer.state.collectAsState()
    val ready = remember(version, voice.id) { ChatFiles.exists(context, voice.id) }
    val gone = remember(version, voice.id) { ChatFiles.gone(context, voice.id) }
    LaunchedEffect(ready, gone) { if (!ready && !gone) onReady() }
    LaunchedEffect(Unit) { VoiceText.load(context) }
    val mineNow = playing?.takeIf { it.id == voice.id }
    val progress = if (mineNow != null && mineNow.duration > 0) mineNow.position / mineNow.duration.toFloat() else 0f
    val fg = if (mine) Color.White else accent
    val bars = voice.wave.ifEmpty { "3".repeat(48) }
    Row(
        modifier = modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (mine) Color.White.copy(alpha = 0.22f) else accent.copy(alpha = 0.22f))
                .clickable(enabled = ready) { VoicePlayer.toggle(context, voice.id) },
            contentAlignment = Alignment.Center,
        ) {
            when {
                gone -> Icon(painterResource(R.drawable.ic_error), contentDescription = "Голосового нет на телефоне", tint = fg, modifier = Modifier.size(18.dp))
                !ready -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
                mineNow != null && !mineNow.paused -> Icon(painterResource(R.drawable.ic_pause), contentDescription = "Пауза", tint = fg, modifier = Modifier.size(20.dp))
                else -> Icon(painterResource(R.drawable.ic_play), contentDescription = "Слушать", tint = fg, modifier = Modifier.size(22.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Canvas(modifier = Modifier.width(150.dp).height(26.dp)) {
                val n = bars.length
                val step = size.width / n
                val w = step * 0.55f
                for (i in 0 until n) {
                    val v = (bars[i].digitToIntOrNull() ?: 3) / 9f
                    val h = max(3.dp.toPx(), v * size.height)
                    val done = i / n.toFloat() < progress
                    drawLine(
                        color = if (done) fg else fg.copy(alpha = 0.45f),
                        start = Offset(i * step + w / 2f, (size.height - h) / 2f),
                        end = Offset(i * step + w / 2f, (size.height + h) / 2f),
                        strokeWidth = w,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (gone) "стёрто из облака, верни из архива" else formatDuration(if (mineNow != null) mineNow.position else voice.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.75f),
                )
                // «Текст»: расшифровать голосовое (нажал ещё раз — спрятать текст).
                if (ready && !gone && VoiceText.possible()) {
                    val texts by VoiceText.state.collectAsState()
                    val shown = texts[voice.id]
                    Text(
                        text = if (shown is VoiceText.State.Done) "скрыть текст" else "текст",
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(fg.copy(alpha = 0.18f))
                            .clickable {
                                if (shown is VoiceText.State.Done || shown is VoiceText.State.Failed) VoiceText.hide(voice.id) else VoiceText.start(context, voice.id)
                            }
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                            .testTag("voice-text"),
                    )
                }
                // Скорость видна, пока голосовое играет (или стоит на паузе): нажал — быстрее.
                if (mineNow != null) {
                    val speed by VoicePlayer.speed.collectAsState()
                    Text(
                        text = speedLabel(speed),
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(fg.copy(alpha = 0.18f))
                            .clickable { VoicePlayer.nextSpeed(context) }
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                            .testTag("voice-speed"),
                    )
                }
            }
        }
    }
}

internal fun speedLabel(speed: Float): String = when (speed) {
    1.5f -> "1,5x"
    2f -> "2x"
    else -> "1x"
}

// ---------- кружочки ----------

/**
 * Идущая запись кружочка: кнопка отпущена — [stopRequested], увёл влево — [cancelled].
 * Увёл вверх — [locked]: запись идёт без рук, пока не нажмёшь «Отправить» или «Отмена».
 * [front] — какая камера снимает; переключать можно прямо во время записи.
 */
@Stable
internal class CircleSession {
    var stopRequested by mutableStateOf(false)
    var cancelled by mutableStateOf(false)
    var startedAt by mutableLongStateOf(0L)
    var locked by mutableStateOf(false)
    var front by mutableStateOf(true)
}

private suspend fun cameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
}

/**
 * Запись кружочка: камера в круге, по краю бежит время (до 30 секунд).
 * Отпустил кнопку — кружочек уходит, увёл палец влево — запись отменяется, вверх — пишется без рук.
 * Камеру можно перевернуть во время записи: запись при этом не прерывается.
 */
@SuppressLint("MissingPermission")
@androidx.annotation.OptIn(markerClass = [androidx.camera.video.ExperimentalPersistentRecording::class])
@Composable
internal fun CircleRecorderOverlay(session: CircleSession, accent: Color, onResult: (ByteArray?, Long) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val holder = remember { arrayOfNulls<Recording>(1) }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val file = remember { File(context.cacheDir, "circle-${System.currentTimeMillis()}.mp4") }
    var finished by remember { mutableStateOf(false) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    // Камера, превью и запись — чтобы перевернуть камеру, не останавливая запись.
    val useCases = remember { arrayOfNulls<androidx.camera.core.UseCase>(2) }
    var bound by remember { mutableStateOf(false) }
    var boundFront by remember { mutableStateOf(true) }
    var canFlip by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val provider = runCatching { cameraProvider(context) }.getOrNull()
        if (provider == null) {
            onResult(null, 0L)
            return@LaunchedEffect
        }
        providerHolder[0] = provider
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .setTargetVideoEncodingBitRate(600_000)
            .build()
        val capture = VideoCapture.withOutput(recorder)
        useCases[0] = preview
        useCases[1] = capture
        canFlip = runCatching {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        }.getOrDefault(false)
        try {
            provider.unbindAll()
            provider.bindToLifecycle(owner, if (session.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            boundFront = session.front
            bound = true
        } catch (e: Exception) {
            onResult(null, 0L)
            return@LaunchedEffect
        }
        if (session.stopRequested || session.cancelled) {
            provider.unbindAll()
            onResult(null, 0L)
            return@LaunchedEffect
        }
        val options = FileOutputOptions.Builder(file).setDurationLimitMillis(CIRCLE_MAX_MS).build()
        // «Постоянная» запись переживает смену камеры: при перевороте она не обрывается.
        holder[0] = capture.output.prepareRecording(context, options)
            .withAudioEnabled()
            .asPersistentRecording()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Start) session.startedAt = SystemClock.elapsedRealtime()
                if (event is VideoRecordEvent.Finalize && !finished) {
                    finished = true
                    val duration = event.recordingStats.recordedDurationNanos / 1_000_000L
                    val failed = event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                    val bytes = if (session.cancelled || failed || duration < 700) null else runCatching { file.readBytes() }.getOrNull()
                    file.delete()
                    provider.unbindAll()
                    onResult(bytes, duration)
                }
            }
    }
    LaunchedEffect(session.stopRequested, session.cancelled) {
        if (session.stopRequested || session.cancelled) holder[0]?.stop()
    }
    // Перевернуть камеру: те же превью и запись привязываются к другой камере.
    LaunchedEffect(session.front, bound) {
        if (!bound || finished || session.front == boundFront) return@LaunchedEffect
        val provider = providerHolder[0] ?: return@LaunchedEffect
        val preview = useCases[0] ?: return@LaunchedEffect
        val capture = useCases[1] ?: return@LaunchedEffect
        val selector = if (session.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val switched = runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, preview, capture)
        }.isSuccess
        if (switched) {
            boundFront = session.front
        } else {
            // Другая камера не открылась — возвращаемся к прежней, запись продолжается.
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, if (boundFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }
            session.front = boundFront
        }
    }
    LaunchedEffect(Unit) {
        while (!finished) {
            withFrameNanos { }
            val started = session.startedAt
            elapsed = if (started == 0L) 0f else ((SystemClock.elapsedRealtime() - started) / CIRCLE_MAX_MS.toFloat()).coerceIn(0f, 1f)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.stop()
            providerHolder[0]?.unbindAll()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            // Пока идёт запись, нажатия не проходят к переписке под затемнением.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .size(260.dp)
                        .clip(CircleShape),
                )
                Canvas(modifier = Modifier.size(276.dp)) {
                    drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(4.dp.toPx()))
                    drawArc(accent, -90f, 360f * elapsed, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Text(
                text = formatDuration((elapsed * CIRCLE_MAX_MS).toLong()),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = if (session.locked) "Запись идёт без рук" else "Отпусти — отправить, влево — отмена, вверх — без рук",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                if (session.locked) {
                    CircleControl(R.drawable.ic_delete, "Отменить кружочек", Color.White.copy(alpha = 0.16f)) { session.cancelled = true }
                }
                if (canFlip) {
                    CircleControl(R.drawable.ic_flip_camera, "Перевернуть камеру", Color.White.copy(alpha = 0.16f), testTag = "circle-flip") {
                        session.front = !session.front
                    }
                }
                if (session.locked) {
                    CircleControl(R.drawable.ic_send, "Отправить кружочек", accent, testTag = "circle-send") { session.stopRequested = true }
                }
            }
        }
    }
}

/** Круглая кнопка под кружочком: отмена, переворот камеры, отправка. */
@Composable
private fun CircleControl(icon: Int, description: String, background: Color, testTag: String = "", onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

internal const val CIRCLE_MAX_MS = 30_000L

private fun firstFrame(file: File): Bitmap? = runCatching {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(file.absolutePath)
        r.getFrameAtTime(0)
    } finally {
        r.release()
    }
}.getOrNull()

/** Кружочек в ленте: первый кадр, по нажатию играет со звуком, по краю бежит время. */
@Composable
internal fun CircleBubble(circle: ChatCircle, accent: Color, onReady: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by ChatFiles.version.collectAsState()
    val ready = remember(version, circle.id) { ChatFiles.exists(context, circle.id) }
    val gone = remember(version, circle.id) { ChatFiles.gone(context, circle.id) }
    LaunchedEffect(ready, gone) { if (!ready && !gone) onReady() }
    val frame by produceState<Bitmap?>(null, ready, circle.id) {
        if (ready) value = withContext(Dispatchers.IO) { firstFrame(ChatFiles.file(context, circle.id)) }
    }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .size(210.dp)
            .clip(CircleShape)
            .background(Palette.Chip)
            .border(2.dp, accent.copy(alpha = 0.5f), CircleShape)
            .clickable(enabled = ready) {
                VoicePlayer.stop()
                playing = !playing
            },
        contentAlignment = Alignment.Center,
    ) {
        val image = frame
        if (playing && ready) {
            CircleVideo(
                file = ChatFiles.file(context, circle.id),
                onProgress = { progress = it },
                onEnd = {
                    playing = false
                    progress = 0f
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (image != null) {
            Image(bitmap = image.asImageBitmap(), contentDescription = "Кружочек", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        when {
            gone -> Text("Кружочек стёрт из облака", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            !ready -> CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp, color = accent)
            !playing -> Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_play), contentDescription = "Смотреть", tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
        if (playing) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(accent, -90f, 360f * progress, false, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Text(
            text = formatDuration(circle.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Видео в круге: TextureView обрезается по кругу, картинка заполняет круг (по центру). */
@Composable
private fun CircleVideo(file: File, onProgress: (Float) -> Unit, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(file) {
        onDispose {
            player[0]?.let {
                runCatching { it.stop() }
                it.release()
            }
            player[0] = null
        }
    }
    LaunchedEffect(file) {
        while (true) {
            delay(60)
            val p = player[0] ?: continue
            val d = runCatching { p.duration }.getOrDefault(0)
            if (d > 0) onProgress(runCatching { p.currentPosition }.getOrDefault(0) / d.toFloat())
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        val view = this@apply
                        val p = MediaPlayer()
                        try {
                            p.setSurface(Surface(texture))
                            p.setDataSource(file.absolutePath)
                            p.setOnVideoSizeChangedListener { _, vw, vh -> centerCrop(view, vw, vh) }
                            p.setOnCompletionListener { onEnd() }
                            p.prepare()
                            p.start()
                            player[0] = p
                        } catch (e: Exception) {
                            p.release()
                            onEnd()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

private fun centerCrop(view: TextureView, videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0 || view.width <= 0 || view.height <= 0) return
    val viewRatio = view.width / view.height.toFloat()
    val videoRatio = videoWidth / videoHeight.toFloat()
    val sx: Float
    val sy: Float
    if (videoRatio > viewRatio) {
        sx = videoRatio / viewRatio
        sy = 1f
    } else {
        sx = 1f
        sy = viewRatio / videoRatio
    }
    view.setTransform(Matrix().apply { setScale(sx, sy, view.width / 2f, view.height / 2f) })
}

/** Кнопка записи сразу шлёт готовое: голосовое или кружочек. */
internal fun sendRecorded(context: Context, voice: VoiceRecorder.Result?, replyTo: String?) {
    if (voice != null) ChatSync.sendVoice(context, voice.bytes, voice.durationMs, voice.wave, replyTo)
}

/** Расшифровка голосового под ним: пока идёт — «расшифровываю…» и то, что уже разобрано. */
@Composable
internal fun VoiceTranscript(id: String, color: Color, faint: Color, modifier: Modifier = Modifier) {
    val texts by VoiceText.state.collectAsState()
    val state = texts[id] ?: return
    val (text, dim) = when (state) {
        is VoiceText.State.Working -> (if (state.partial.isBlank()) "расшифровываю…" else state.partial + " …") to true
        is VoiceText.State.Done -> state.text to false
        is VoiceText.State.Failed -> ("Не вышло: " + state.why) to true
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.let { if (dim) it.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) else it },
        color = if (dim) faint else color,
        modifier = modifier.padding(start = 13.dp, end = 13.dp, bottom = 8.dp).testTag("voice-transcript"),
    )
}
