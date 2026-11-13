package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Подготовка голосового к распознаванию: каналы, частота, байты. */
class VoicePcmTest {

    private fun sine(rate: Int, seconds: Double, hz: Double, channels: Int = 1): ShortArray {
        val frames = (rate * seconds).toInt()
        return ShortArray(frames * channels) { i -> (sin(2 * PI * hz * (i / channels) / rate) * 12_000).toInt().toShort() }
    }

    private fun crossings(s: ShortArray): Int = (1 until s.size).count { (s[it - 1] < 0) != (s[it] < 0) }

    @Test
    fun recorderRateIsConvertedTo16kWithoutChangingPitch() {
        val input = sine(22_050, 2.0, 440.0)
        val out = VoicePcm.toMono16k(input, 22_050, 1)
        assertEquals(32_000.0, out.size.toDouble(), 2.0)
        // 440 Гц за 2 секунды — около 1760 переходов через ноль, как и было.
        assertEquals(1760.0, crossings(out).toDouble(), 20.0)
        assertTrue(out.maxOf { abs(it.toInt()) } in 11_000..12_100)
    }

    @Test
    fun stereoIsAveraged() {
        val stereo = shortArrayOf(1000, 3000, -2000, 0, 32767, 32767)
        assertEquals(listOf<Short>(2000, -1000, 32767), VoicePcm.toMono16k(stereo, 16_000, 2).toList())
    }

    @Test
    fun otherRatesAndEmptyInput() {
        assertEquals(16_000, VoicePcm.toMono16k(sine(48_000, 1.0, 300.0, channels = 2), 48_000, 2).size)
        assertEquals(16_000, VoicePcm.toMono16k(sine(8_000, 1.0, 300.0), 8_000, 1).size)
        assertEquals(0, VoicePcm.toMono16k(ShortArray(0), 22_050, 1).size)
        assertEquals(0, VoicePcm.toMono16k(shortArrayOf(1, 2, 3), 0, 1).size)
    }

    @Test
    fun floatSoundIsClamped() {
        assertEquals(listOf<Short>(0, 32767, -32768, 16383), VoicePcm.fromFloat(floatArrayOf(0f, 1.5f, -2f, 0.5f)).toList())
    }

    @Test
    fun bytesAreLittleEndianWithSilenceAtTheEnd() {
        val bytes = VoicePcm.toBytes(shortArrayOf(0x1234, -2), silenceMs = 1000)
        assertEquals(4 + 32_000, bytes.size)
        assertEquals(0x34.toByte(), bytes[0])
        assertEquals(0x12.toByte(), bytes[1])
        assertEquals(0xFE.toByte(), bytes[2])
        assertEquals(0xFF.toByte(), bytes[3])
        assertTrue(bytes.drop(4).all { it == 0.toByte() })
    }

    @Test
    fun recognizedPiecesAreJoinedNeatly() {
        assertEquals("Привет как дела", VoicePcm.join(listOf(" привет ", "", "как   дела")))
        assertEquals("", VoicePcm.join(listOf(" ", "")))
    }
}
