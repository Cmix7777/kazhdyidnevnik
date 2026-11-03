package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/** Свои стикеры: квадратные картинки 512×512 с белой обводкой, лежат на телефоне. */
object StickerStore {

    const val SIDE = 512

    private val mutableVersion = MutableStateFlow(0)
    val version: StateFlow<Int> = mutableVersion.asStateFlow()

    private fun dir(context: Context): File = File(context.applicationContext.filesDir, "stickers").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles()?.filter { it.isFile && it.name.endsWith(".webp") }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun add(context: Context, bytes: ByteArray): File = addNamed(context, ChatFiles.newId() + ".webp", bytes)

    fun addNamed(context: Context, name: String, bytes: ByteArray): File {
        val file = File(dir(context), name)
        file.writeBytes(bytes)
        mutableVersion.update { it + 1 }
        return file
    }

    fun remove(file: File) {
        file.delete()
        mutableVersion.update { it + 1 }
    }

    /** Стикер из фото: квадрат по центру, скруглённые углы и белая обводка. */
    fun fromPhoto(context: Context, uri: Uri): ByteArray? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val photo = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = min(info.size.width, info.size.height)
            if (side > SIDE * 2) {
                val k = SIDE * 2f / side
                decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
            }
        }
        val out = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val border = SIDE * 0.035f
        val radius = SIDE * 0.16f
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        canvas.drawRoundRect(RectF(0f, 0f, SIDE.toFloat(), SIDE.toFloat()), radius, radius, white)
        val inner = RectF(border, border, SIDE - border, SIDE - border)
        val side = min(photo.width, photo.height).toFloat()
        val scale = inner.width() / side
        val matrix = Matrix().apply {
            postTranslate(-(photo.width - side) / 2f, -(photo.height - side) / 2f)
            postScale(scale, scale)
            postTranslate(border, border)
        }
        val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        canvas.drawRoundRect(inner, radius - border, radius - border, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        encode(out)
    }.getOrNull()

    /** Стикер из рисунка: прозрачный фон, обводка уже нарисована. */
    fun encode(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        bitmap.compress(format, 82, out)
        return out.toByteArray()
    }
}
