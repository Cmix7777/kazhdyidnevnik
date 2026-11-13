package io.github.cmix7777.kazhdyidnevnik

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.cmix7777.kazhdyidnevnik.data.VoicePcm
import io.github.cmix7777.kazhdyidnevnik.service.ChatFiles
import io.github.cmix7777.kazhdyidnevnik.service.VoiceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Расшифровка голосовых на настоящем Android: запись в том же виде, что пишет приложение (AAC 22 кГц, моно),
 * раскодируется в звук для распознавания; сама расшифровка заканчивается понятным ответом, а не вечным «расшифровываю…».
 */
@RunWith(AndroidJUnit4::class)
class VoiceTextTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()

    /** Синус [hz] длиной [seconds] в файл .m4a, как у голосовых. */
    private fun recordTone(file: File, seconds: Double = 2.0, hz: Double = 440.0) {
        val rate = 22_050
        val pcm = ShortArray((rate * seconds).toInt()) { (sin(2 * PI * hz * it / rate) * 12_000).toInt().toShort() }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var pos = 0
        var inputDone = false
        val info = MediaCodec.BufferInfo()
        while (true) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buffer = codec.getInputBuffer(i)!!
                    buffer.clear()
                    val n = minOf(buffer.capacity() / 2, 2_048, pcm.size - pos)
                    if (n <= 0) {
                        codec.queueInputBuffer(i, 0, 0, pos * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val bytes = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (k in 0 until n) bytes.putShort(pcm[pos + k])
                        buffer.put(bytes.array())
                        codec.queueInputBuffer(i, 0, n * 2, pos * 1_000_000L / rate, 0)
                        pos += n
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
            } else if (o >= 0) {
                val out = codec.getOutputBuffer(o)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && track >= 0) muxer.writeSampleData(track, out, info)
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }

    @Test
    fun voiceFileIsDecodedInto16kSoundWithTheSamePitch() {
        val file = File(context.cacheDir, "tone.m4a")
        recordTone(file, seconds = 2.0, hz = 440.0)
        val bytes = VoiceText.decode(file)
        val tail = VoicePcm.RATE * 2 * 3
        val speech = bytes.size - tail
        // Около двух секунд звука (у AAC бывает небольшая задержка в начале и в конце).
        assertTrue("длина ${speech / 2} отсчётов", speech / 2 in 30_000..36_000)
        val samples = ShortArray(speech / 2) { ((bytes[it * 2].toInt() and 0xFF) or (bytes[it * 2 + 1].toInt() shl 8)).toShort() }
        val crossings = (1 until samples.size).count { (samples[it - 1] < 0) != (samples[it] < 0) }
        assertEquals("тон тот же", 1760.0, crossings.toDouble(), 120.0)
        assertTrue("в конце тишина", bytes.takeLast(tail).all { it == 0.toByte() })
    }

    @Test(timeout = 180_000)
    fun transcriptionEndsWithAnAnswer() {
        val id = "test-voice-${System.currentTimeMillis()}"
        val file = ChatFiles.file(context, id)
        file.parentFile?.mkdirs()
        recordTone(file, seconds = 2.0)
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post { VoiceText.start(context, id) }
        val deadline = System.currentTimeMillis() + 170_000
        var state: VoiceText.State? = null
        while (System.currentTimeMillis() < deadline) {
            state = VoiceText.state.value[id]
            if (state != null && state !is VoiceText.State.Working) break
            Thread.sleep(200)
        }
        Log.i("VoiceTextTest", "VOICE: $state")
        println("VOICE: $state")
        assertTrue("расшифровка зависла: $state", state is VoiceText.State.Done || state is VoiceText.State.Failed)
        if (state is VoiceText.State.Failed) {
            assertTrue(state.why.isNotBlank())
            assertFalse(state.why.contains("Exception"))
        }
        file.delete()
    }
}
