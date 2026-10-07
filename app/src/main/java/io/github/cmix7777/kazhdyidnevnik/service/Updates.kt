package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.cmix7777.kazhdyidnevnik.BuildConfig
import io.github.cmix7777.kazhdyidnevnik.data.Release
import io.github.cmix7777.kazhdyidnevnik.data.ReleaseParser
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Обновление приложения из раздела Releases на GitHub. */
object Updates {

    private const val USER_AGENT = "Kazhdyidnevnik-Android"
    private const val BACKGROUND_EVERY_MS = 12 * 60 * 60 * 1000L

    /** Узнать последнюю версию на GitHub. Ответ запоминается. */
    suspend fun latest(context: Context, timeoutMs: Int = 15_000): Release = withContext(Dispatchers.IO) {
        val release = try {
            fromApi(timeoutMs)
        } catch (e: Exception) {
            // API GitHub ограничивает число запросов — тогда смотрим, куда ведёт ссылка на последнюю версию.
            fromRedirect(timeoutMs)
        }
        val settings = AppSettings(context)
        settings.lastUpdateCheckMillis = System.currentTimeMillis()
        settings.knownRelease = release
        release
    }

    fun isNewer(release: Release?): Boolean = release != null && release.versionCode > BuildConfig.VERSION_CODE

    private fun fromApi(timeoutMs: Int): Release {
        val body = Jsoup.connect("https://api.github.com/repos/${ReleaseParser.REPO}/releases/latest")
            .ignoreContentType(true)
            .userAgent(USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .timeout(timeoutMs)
            .execute()
            .body()
        return ReleaseParser.fromApi(body) ?: throw IOException("Непонятный ответ GitHub")
    }

    private fun fromRedirect(timeoutMs: Int): Release {
        val response = Jsoup.connect("https://github.com/${ReleaseParser.REPO}/releases/latest")
            .followRedirects(false)
            .ignoreContentType(true)
            .userAgent(USER_AGENT)
            .timeout(timeoutMs)
            .execute()
        val location = response.header("Location") ?: throw IOException("GitHub не ответил")
        return ReleaseParser.fromTagUrl(location) ?: throw IOException("Непонятный ответ GitHub")
    }

    /** Фоновая проверка не чаще раза в 12 часов, уведомление — один раз на каждую новую версию. */
    suspend fun checkInBackground(context: Context) {
        val settings = AppSettings(context)
        if (System.currentTimeMillis() - settings.lastUpdateCheckMillis < BACKGROUND_EVERY_MS) return
        val release = latest(context)
        if (isNewer(release) && settings.updateNotifiedFor != release.versionCode) {
            Notifier.update(context, release)
            settings.updateNotifiedFor = release.versionCode
        }
    }

    /** Скачать APK. [onProgress] получает долю от 0 до 1 (или -1, если размер неизвестен). */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "kazhdyidnevnik-${release.versionName}.apk")

            val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("GitHub ответил ${connection.responseCode}")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastReported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                val percent = (copied * 100 / total).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    onProgress(copied.toFloat() / total)
                                }
                            } else {
                                onProgress(-1f)
                            }
                        }
                        if (total > 0 && copied != total) throw IOException("Файл скачался не полностью")
                    }
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            } finally {
                connection.disconnect()
            }
            file
        }

    /** Разрешено ли этому приложению ставить обновления (настройка «Установка неизвестных приложений»). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Открыть системный установщик. Дальше Android сам спросит «Обновить приложение?». */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Удалить скачанные файлы, если обновление уже установлено. */
    fun cleanup(context: Context) {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }
}
