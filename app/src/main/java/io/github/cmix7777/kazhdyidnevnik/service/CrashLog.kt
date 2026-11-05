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
                    append(Log.getStackTraceString(error))
                }
                File(app.filesDir, FILE).writeText(text.take(30_000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
