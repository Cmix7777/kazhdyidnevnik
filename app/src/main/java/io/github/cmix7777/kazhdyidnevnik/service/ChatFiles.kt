package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import io.github.cmix7777.kazhdyidnevnik.data.ChatCloud
import io.github.cmix7777.kazhdyidnevnik.data.ChatPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Фото переписки на телефоне: сжатие перед отправкой, хранение и уменьшенные копии для экрана. */
object ChatFiles {

    class Prepared(val photo: ChatPhoto, val bytes: ByteArray)

    private const val MAX_SIDE = 1600
    private val random = SecureRandom()

    /** Меняется, когда скачалось новое фото: экран перерисовывает заглушки. */
    private val mutableVersion = MutableStateFlow(0)
    val version: StateFlow<Int> = mutableVersion.asStateFlow()

    private val cache = object : LruCache<String, Bitmap>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    private fun dir(context: Context): File = File(context.filesDir, "chat-files").apply { mkdirs() }

    fun file(context: Context, id: String): File = File(dir(context), "$id.jpg")

    fun exists(context: Context, id: String): Boolean = id.isNotEmpty() && file(context, id).exists()

    fun read(context: Context, id: String): ByteArray? = runCatching { file(context, id).readBytes() }.getOrNull()

    fun save(context: Context, id: String, bytes: ByteArray) {
        val target = file(context, id)
        val temp = File(target.parentFile, "$id.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.writeBytes(bytes)
            temp.delete()
        }
        mutableVersion.update { it + 1 }
    }

    private fun newId(): String {
        val bytes = ByteArray(8).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Сжать фото из галереи: не больше 1600 точек по длинной стороне и не больше
     * [ChatCloud.MAX_PHOTO_BYTES] байт (так оно в зашифрованном виде проходит правила базы).
     * Поворот из EXIF ImageDecoder учитывает сам. null — фото не открылось.
     */
    fun prepare(context: Context, uri: Uri): Prepared? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        var bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val scale = min(1f, MAX_SIDE / max(width, height).toFloat())
            if (scale < 1f) {
                decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
            }
        }
        // Прозрачные места в JPEG стали бы чёрными: подкладываем белый.
        if (bitmap.hasAlpha()) {
            val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(android.graphics.Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            bitmap = flat
        }
        repeat(5) {
            for (quality in intArrayOf(85, 76, 66, 56)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= ChatCloud.MAX_PHOTO_BYTES) {
                    return@runCatching Prepared(ChatPhoto(newId(), bitmap.width, bitmap.height), out.toByteArray())
                }
            }
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * 0.75f).roundToInt().coerceAtLeast(1),
                (bitmap.height * 0.75f).roundToInt().coerceAtLeast(1),
                true,
            )
        }
        null
    }.getOrNull()

    /** Уже готовая уменьшенная копия (без чтения диска). */
    fun cached(id: String, maxSide: Int): Bitmap? = cache.get("$id@$maxSide")

    /** Уменьшенная копия для экрана, не больше [maxSide] точек по длинной стороне. null — фото ещё нет. */
    fun decode(context: Context, id: String, maxSide: Int): Bitmap? {
        val key = "$id@$maxSide"
        cache.get(key)?.let { return it }
        val source = file(context, id).takeIf { it.exists() } ?: return null
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val scale = min(1f, maxSide / max(width, height).toFloat())
                if (scale < 1f) {
                    decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
                }
            }
        }.getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}
