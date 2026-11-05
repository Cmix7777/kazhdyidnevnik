package io.github.cmix7777.kazhdyidnevnik.service

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import io.github.cmix7777.kazhdyidnevnik.BuildConfig
import java.io.File
import java.time.LocalDateTime

/**
 * Если приложение вылетело, причина остаётся в файле на телефоне. При следующем запуске её можно
 * скопировать и прислать: по ней видно, где именно сломалось. Никуда сама не отправляется.
 */
object CrashLog {

    private const val FILE = "last-crash.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    appendLine("Каждыйдневник ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Когда: ${LocalDateTime.now().withNano(0)}")
                    appendLine("Поток: ${thread.name}")
                    append(short(error))
                    appendLine()
                    appendLine("--- полностью ---")
                    append(Log.getStackTraceString(error))
                }
                File(app.filesDir, FILE).writeText(text.take(30_000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Главное коротко, чтобы влезло в сообщение: сама ошибка, её причины и только строчки из кода приложения
     * (плюс по три первых строки каждой ошибки). Полный текст идёт ниже.
     */
    private fun short(error: Throwable): String = buildString {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 6) {
            appendLine((if (depth == 0) "" else "Причина: ") + current.javaClass.name + ": " + current.message.orEmpty().take(400))
            current.stackTrace.forEachIndexed { index, frame ->
                if (index < 3 || frame.className.startsWith("io.github.cmix7777")) appendLine("  at $frame")
            }
            current = current.cause?.takeIf { it !== current }
            depth++
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
