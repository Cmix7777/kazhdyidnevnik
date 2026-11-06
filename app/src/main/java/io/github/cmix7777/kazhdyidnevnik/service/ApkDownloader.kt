package io.github.cmix7777.kazhdyidnevnik.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest

/** Как идёт скачивание: сколько уже есть, сколько всего (0 — неизвестно), с запасного ли адреса и медленно ли. */
data class DownloadProgress(
    val done: Long,
    val total: Long,
    val mirror: Boolean = false,
    val slow: Boolean = false,
) {
    /** Доля от 0 до 1 или -1, если размер неизвестен. */
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
}

/** Скачать не вышло ни с одного адреса; текст можно показать человеку. */
class DownloadException(message: String) : IOException(message)

/**
 * Скачивание файла обновления, которое не виснет.
 *
 * Раньше APK качался одним запросом без общего ограничения: если GitHub отдавал байты по капле
 * (так бывает у мобильных операторов) или соединение замирало, полоска стояла на месте бесконечно.
 * Теперь:
 * - замершее соединение обрывается через [stallMs] и продолжается с того же места (запрос Range);
 * - если адрес отдаёт медленнее [slowBytesPerSecond] дольше [slowWindowMs], скачивание переходит
 *   на следующий адрес (копия на jsDelivr) и тоже продолжает с уже скачанного;
 * - после [attempts] неудач на каждый адрес — понятная ошибка, а не вечная полоска;
 * - готовый файл сверяется по размеру и, если известна, по сумме SHA-256.
 */
class ApkDownloader(
    private val connectTimeoutMs: Int = 20_000,
    private val stallMs: Int = 20_000,
    private val slowWindowMs: Long = 30_000,
    private val slowBytesPerSecond: Long = 12_000,
    private val attempts: Int = 2,
    private val retryDelayMs: Long = 1_500,
    private val userAgent: String = "Kazhdyidnevnik-Android",
    private val now: () -> Long = System::currentTimeMillis,
) {

    private enum class Outcome { COMPLETE, SLOW }

    suspend fun download(
        sources: List<String>,
        target: File,
        expectedSize: Long = 0,
        sha256: String? = null,
        onProgress: (DownloadProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty())
        target.parentFile?.mkdirs()
        target.delete()
        var index = 0
        var failures = 0
        var slowSwitches = 0
        val maxFailures = attempts * sources.size
        while (true) {
            currentCoroutineContext().ensureActive()
            // Переход на другой адрес из-за медленности — по одному разу на адрес, дальше качаем, как получается.
            val canSwitch = sources.size > 1 && slowSwitches < sources.size - 1
            val outcome = try {
                fetch(sources[index], target, expectedSize, mirror = index > 0, canSwitch, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                failures++
                if (failures >= maxFailures) {
                    target.delete()
                    throw DownloadException(explain(e))
                }
                index = (index + 1) % sources.size
                delay(retryDelayMs)
                continue
            }
            when (outcome) {
                Outcome.COMPLETE -> break
                Outcome.SLOW -> {
                    slowSwitches++
                    index = (index + 1) % sources.size
                }
            }
        }
        val size = target.length()
        if (expectedSize > 0 && size != expectedSize) {
            target.delete()
            throw DownloadException("файл скачался не полностью")
        }
        if (!sha256.isNullOrBlank() && !sha256.equals(sha256Of(target), ignoreCase = true)) {
            target.delete()
            throw DownloadException("файл скачался с ошибками, попробуй ещё раз")
        }
        target
    }

    /** Один запрос: дописывает файл с того места, где он кончается. */
    private suspend fun fetch(
        url: String,
        target: File,
        expectedSize: Long,
        mirror: Boolean,
        canSwitch: Boolean,
        onProgress: (DownloadProgress) -> Unit,
    ): Outcome {
        var from = target.length()
        if (expectedSize > 0 && from >= expectedSize) {
            if (from == expectedSize) return Outcome.COMPLETE
            target.delete()
            from = 0
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = stallMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (from > 0) connection.setRequestProperty("Range", "bytes=$from-")
        try {
            val code = connection.responseCode
            val append = when {
                code == HttpURLConnection.HTTP_PARTIAL && from > 0 -> true
                code == HttpURLConnection.HTTP_OK -> false
                code == 416 && expectedSize > 0 && from == expectedSize -> return Outcome.COMPLETE
                code == 416 -> {
                    // Сервер не понял, откуда продолжать: начнём заново.
                    target.delete()
                    throw IOException("сервер не продолжил скачивание")
                }
                else -> throw IOException("сервер ответил $code")
            }
            if (!append) from = 0
            val length = connection.contentLengthLong
            val total = when {
                expectedSize > 0 -> expectedSize
                length > 0 -> from + length
                else -> 0L
            }
            var done = from
            var windowStart = now()
            var windowBytes = 0L
            var slow = false
            onProgress(DownloadProgress(done, total, mirror, slow))
            FileOutputStream(target, append).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var reported = now()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = try {
                            input.read(buffer)
                        } catch (e: SocketTimeoutException) {
                            throw IOException("соединение замерло", e)
                        }
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        windowBytes += read
                        val time = now()
                        val elapsed = time - windowStart
                        if (elapsed >= slowWindowMs) {
                            slow = windowBytes * 1000 / elapsed.coerceAtLeast(1) < slowBytesPerSecond
                            if (slow && canSwitch && (total == 0L || done < total)) {
                                onProgress(DownloadProgress(done, total, mirror, slow = true))
                                return Outcome.SLOW
                            }
                            windowStart = time
                            windowBytes = 0
                        }
                        if (time - reported >= 200 || (total > 0 && done >= total)) {
                            reported = time
                            onProgress(DownloadProgress(done, total, mirror, slow))
                        }
                    }
                }
            }
            if (total > 0 && done < total) throw IOException("соединение оборвалось")
            if (total > 0 && done > total) {
                target.delete()
                throw IOException("пришло больше, чем нужно")
            }
            onProgress(DownloadProgress(done, total, mirror, slow))
            return Outcome.COMPLETE
        } finally {
            connection.disconnect()
        }
    }

    private fun explain(e: IOException): String {
        val text = e.message.orEmpty()
        return when {
            e is java.net.UnknownHostException -> "нет интернета или GitHub недоступен"
            e is SocketTimeoutException || e is java.net.ConnectException -> "GitHub не отвечает"
            text.startsWith("сервер ответил ") -> "сервер ответил ошибкой " + text.removePrefix("сервер ответил ")
            text.firstOrNull() in 'а'..'я' -> text
            else -> "нет связи с GitHub"
        }
    }

    companion object {
        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
