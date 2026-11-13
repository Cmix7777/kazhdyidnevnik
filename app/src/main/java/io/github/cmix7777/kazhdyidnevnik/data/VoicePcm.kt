package io.github.cmix7777.kazhdyidnevnik.data

/** Звук для распознавания речи: моно, 16 кГц, 16 бит. Без Android, чтобы проверять тестами. */
object VoicePcm {
    const val RATE = 16_000

    /** Свести каналы в один и пересчитать частоту в [RATE] (линейно: для речи этого хватает). */
    fun toMono16k(samples: ShortArray, rate: Int, channels: Int): ShortArray {
        val ch = channels.coerceAtLeast(1)
        val frames = samples.size / ch
        if (frames == 0 || rate <= 0) return ShortArray(0)
        val mono = FloatArray(frames) { f ->
            var sum = 0f
            for (c in 0 until ch) sum += samples[f * ch + c]
            sum / ch
        }
        if (rate == RATE) return ShortArray(frames) { clamp(mono[it]) }
        val outSize = (frames.toLong() * RATE / rate).toInt().coerceAtLeast(1)
        val step = rate.toDouble() / RATE
        return ShortArray(outSize) { i ->
            val pos = i * step
            val a = pos.toInt().coerceAtMost(frames - 1)
            val b = (a + 1).coerceAtMost(frames - 1)
            val t = (pos - a).toFloat()
            clamp(mono[a] * (1f - t) + mono[b] * t)
        }
    }

    /** Звук с плавающей точкой (-1..1) в 16 бит. */
    fun fromFloat(samples: FloatArray): ShortArray = ShortArray(samples.size) { clamp(samples[it] * 32767f) }

    /** Байты little-endian и [silenceMs] тишины в конце: распознавание видит, где фраза кончилась. */
    fun toBytes(samples: ShortArray, silenceMs: Int = 0): ByteArray {
        val tail = RATE * silenceMs / 1000
        val out = ByteArray((samples.size + tail) * 2)
        for (i in samples.indices) {
            val v = samples[i].toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** Склеить куски распознанного текста: без лишних пробелов, первая буква заглавная. */
    fun join(parts: List<String>): String {
        val text = parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ").replace(Regex("\\s+"), " ")
        return text.replaceFirstChar { it.uppercaseChar() }
    }

    private fun clamp(v: Float): Short = v.coerceIn(-32768f, 32767f).toInt().toShort()
}
