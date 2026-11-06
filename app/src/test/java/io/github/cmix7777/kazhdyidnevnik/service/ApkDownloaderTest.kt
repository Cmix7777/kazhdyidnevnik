package io.github.cmix7777.kazhdyidnevnik.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import java.util.Random

/**
 * Скачивание обновления на настоящем маленьком сервере: замирающем, медленном, обрывающемся,
 * не понимающем Range и отвечающем ошибками. Полоска должна дойти до конца или честно сказать, что не вышло.
 */
class ApkDownloaderTest {

    private val apk = ByteArray(300_000).also { Random(11).nextBytes(it) }
    private val dir: File = Files.createTempDirectory("apk").toFile()
    private val target = File(dir, "app.apk")
    private val servers = mutableListOf<HttpServer>()
    private val ranges = Collections.synchronizedList(mutableListOf<String>())

    @After
    fun stop() {
        servers.forEach { it.stop(0) }
        dir.deleteRecursively()
    }

    /** Сервер, который на каждый запрос зовёт [handler] с номером запроса и началом из Range. */
    private fun server(handler: (exchange: HttpExchange, request: Int, from: Long) -> Unit): String {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var count = 0
        server.createContext("/app.apk") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            ranges += range ?: "-"
            val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
            try {
                handler(exchange, count++, from)
            } catch (e: Exception) {
                // Клиент закрыл соединение — для теста это нормально.
            } finally {
                runCatching { exchange.close() }
            }
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool { task -> Thread(task).apply { isDaemon = true } }
        server.start()
        servers += server
        return "http://127.0.0.1:${server.address.port}/app.apk"
    }

    /** Отдать файл с [from] (206, если просили Range и сервер его понимает), по [chunk] байт с паузой [pauseMs]. */
    private fun send(exchange: HttpExchange, from: Long, honourRange: Boolean = true, upTo: Int = apk.size, chunk: Int = 16_384, pauseMs: Long = 0) {
        val start = if (honourRange) from.toInt() else 0
        val partial = honourRange && from > 0
        val length = apk.size - start
        if (partial) exchange.responseHeaders.add("Content-Range", "bytes $start-${apk.size - 1}/${apk.size}")
        exchange.sendResponseHeaders(if (partial) 206 else 200, length.toLong())
        val body = exchange.responseBody
        var pos = start
        while (pos < upTo) {
            val n = minOf(chunk, upTo - pos)
            body.write(apk, pos, n)
            body.flush()
            pos += n
            if (pauseMs > 0) Thread.sleep(pauseMs)
        }
        // Замирание: байты больше не идут, но соединение не закрывается.
        if (upTo < apk.size) Thread.sleep(5_000)
        body.close()
    }

    private fun downloader() = ApkDownloader(
        connectTimeoutMs = 2_000,
        stallMs = 400,
        slowWindowMs = 600,
        slowBytesPerSecond = 100_000,
        attempts = 3,
        retryDelayMs = 10,
    )

    private fun download(vararg urls: String, size: Long = apk.size.toLong(), sha: String? = null, progress: MutableList<DownloadProgress> = mutableListOf()) =
        runBlocking { downloader().download(urls.toList(), target, size, sha) { progress += it } }

    @Test(timeout = 20_000)
    fun plainDownloadReportsProgressToTheEnd() {
        val url = server { exchange, _, from -> send(exchange, from) }
        val progress = mutableListOf<DownloadProgress>()
        download(url, sha = sha(apk), progress = progress)
        assertArrayEquals(apk, target.readBytes())
        assertEquals(apk.size.toLong(), progress.last().done)
        assertEquals(1f, progress.last().fraction, 0f)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.done >= a.done })
    }

    @Test(timeout = 20_000)
    fun frozenConnectionIsResumedFromTheSamePlace() {
        val url = server { exchange, request, from ->
            if (request == 0) send(exchange, from, upTo = 120_000) else send(exchange, from)
        }
        download(url)
        assertArrayEquals(apk, target.readBytes())
        assertEquals("-", ranges[0])
        assertEquals("bytes=120000-", ranges[1])
    }

    @Test(timeout = 20_000)
    fun brokenServerFallsBackToMirror() {
        val github = server { exchange, _, _ -> exchange.sendResponseHeaders(502, -1) }
        val mirror = server { exchange, _, from -> send(exchange, from) }
        download(github, mirror)
        assertArrayEquals(apk, target.readBytes())
    }

    @Test(timeout = 30_000)
    fun trickleSwitchesToMirrorAndKeepsWhatWasDownloaded() {
        val github = server { exchange, _, from -> send(exchange, from, chunk = 2_000, pauseMs = 100) }
        val mirror = server { exchange, _, from -> send(exchange, from) }
        val progress = mutableListOf<DownloadProgress>()
        download(github, mirror, progress = progress)
        assertArrayEquals(apk, target.readBytes())
        assertTrue(progress.any { it.slow })
        assertTrue(progress.last().mirror)
        // Запасной адрес продолжил с уже скачанного, а не начал заново.
        assertTrue(ranges.last(), ranges.last().startsWith("bytes=") && ranges.last() != "bytes=0-")
    }

    @Test(timeout = 20_000)
    fun serverWithoutRangeStartsOverAndStillGivesTheRightFile() {
        val url = server { exchange, request, from ->
            if (request == 0) send(exchange, from, honourRange = false, upTo = 100_000) else send(exchange, from, honourRange = false)
        }
        download(url)
        assertArrayEquals(apk, target.readBytes())
    }

    @Test(timeout = 20_000)
    fun connectionCutShortIsResumed() {
        val url = server { exchange, request, from ->
            if (request == 0) {
                exchange.sendResponseHeaders(200, apk.size.toLong())
                exchange.responseBody.write(apk, 0, 50_000)
                exchange.responseBody.flush()
                // Обрыв: соединение закрывается раньше обещанной длины.
            } else {
                send(exchange, from)
            }
        }
        download(url)
        assertArrayEquals(apk, target.readBytes())
    }

    @Test(timeout = 20_000)
    fun wrongChecksumIsAnErrorAndLeavesNoFile() {
        val url = server { exchange, _, from -> send(exchange, from) }
        try {
            download(url, sha = "0".repeat(64))
            fail("испорченный файл не должен считаться скачанным")
        } catch (e: DownloadException) {
            assertTrue(e.message!!.contains("ошибками"))
        }
        assertFalse(target.exists())
    }

    @Test(timeout = 20_000)
    fun everythingDownGivesReadableErrorInsteadOfEndlessBar() {
        val github = server { exchange, _, _ -> exchange.sendResponseHeaders(404, -1) }
        val dead = "http://127.0.0.1:1/app.apk"
        try {
            download(github, dead)
            fail("должна быть ошибка")
        } catch (e: DownloadException) {
            assertTrue(e.message!!.isNotBlank())
            assertFalse(e.message!!.contains("Exception"))
        }
        assertFalse(target.exists())
    }

    @Test(timeout = 20_000)
    fun unknownSizeStillCompletes() {
        val url = server { exchange, _, _ ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(apk) }
        }
        val progress = mutableListOf<DownloadProgress>()
        download(url, size = 0, progress = progress)
        assertArrayEquals(apk, target.readBytes())
        assertEquals(-1f, progress.first().fraction, 0f)
    }

    private fun sha(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
