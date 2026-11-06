package io.github.cmix7777.kazhdyidnevnik.service

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Collections
import java.util.Random
import kotlin.concurrent.thread

/**
 * Скачивание обновления на настоящем маленьком сервере: замирающем, медленном, обрывающемся,
 * не понимающем Range и отвечающем ошибками. Полоска должна дойти до конца или честно сказать, что не вышло.
 */
class ApkDownloaderTest {

    private val apk = ByteArray(300_000).also { Random(11).nextBytes(it) }
    private val dir: File = Files.createTempDirectory("apk").toFile()
    private val target = File(dir, "app.apk")
    private val sockets = mutableListOf<ServerSocket>()
    private val ranges = Collections.synchronizedList(mutableListOf<String>())

    @After
    fun stop() {
        sockets.forEach { runCatching { it.close() } }
        dir.deleteRecursively()
    }

    /**
     * Крошечный HTTP-сервер на сокетах (в тестах Android нет готового). На каждый запрос зовёт [handler]
     * с номером запроса, началом из Range и потоком ответа; после ответа соединение закрывается.
     */
    private fun server(handler: (out: OutputStream, request: Int, from: Long) -> Unit): String {
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        sockets += socket
        var count = 0
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client: Socket = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                val number = synchronized(this) { count++ }
                thread(isDaemon = true) {
                    client.use {
                        val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        var range: String? = null
                        while (true) {
                            val line = input.readLine() ?: return@use
                            if (line.isEmpty()) break
                            if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                        }
                        ranges += range ?: "-"
                        val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
                        try {
                            handler(it.getOutputStream(), number, from)
                        } catch (e: IOException) {
                            // Клиент закрыл соединение — для теста это нормально.
                        }
                    }
                }
            }
        }
        return "http://127.0.0.1:${socket.localPort}/app.apk"
    }

    private fun head(out: OutputStream, status: String, vararg headers: String) {
        val text = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            headers.forEach { append(it).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        out.write(text.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun error(out: OutputStream, status: String) = head(out, status, "Content-Length: 0")

    /** Отдать файл с [from] (206, если просили Range и сервер его понимает), по [chunk] байт с паузой [pauseMs]. */
    private fun send(out: OutputStream, from: Long, honourRange: Boolean = true, upTo: Int = apk.size, chunk: Int = 16_384, pauseMs: Long = 0) {
        val start = if (honourRange) from.toInt() else 0
        val partial = honourRange && from > 0
        val length = apk.size - start
        if (partial) {
            head(out, "206 Partial Content", "Content-Length: $length", "Content-Range: bytes $start-${apk.size - 1}/${apk.size}")
        } else {
            head(out, "200 OK", "Content-Length: $length")
        }
        var pos = start
        while (pos < upTo) {
            val n = minOf(chunk, upTo - pos)
            out.write(apk, pos, n)
            out.flush()
            pos += n
            if (pauseMs > 0) Thread.sleep(pauseMs)
        }
        // Замирание: байты больше не идут, но соединение ещё открыто.
        if (upTo < apk.size) Thread.sleep(5_000)
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
        val url = server { out, _, from -> send(out, from) }
        val progress = mutableListOf<DownloadProgress>()
        download(url, sha = sha(apk), progress = progress)
        assertArrayEquals(apk, target.readBytes())
        assertEquals(apk.size.toLong(), progress.last().done)
        assertEquals(1f, progress.last().fraction, 0f)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.done >= a.done })
    }

    @Test(timeout = 20_000)
    fun frozenConnectionIsResumedFromTheSamePlace() {
        val url = server { out, request, from ->
            if (request == 0) send(out, from, upTo = 120_000) else send(out, from)
        }
        download(url)
        assertArrayEquals(apk, target.readBytes())
        assertEquals("-", ranges[0])
        assertEquals("bytes=120000-", ranges[1])
    }

    @Test(timeout = 20_000)
    fun brokenServerFallsBackToMirror() {
        val github = server { out, _, _ -> error(out, "502 Bad Gateway") }
        val mirror = server { out, _, from -> send(out, from) }
        download(github, mirror)
        assertArrayEquals(apk, target.readBytes())
    }

    @Test(timeout = 30_000)
    fun trickleSwitchesToMirrorAndKeepsWhatWasDownloaded() {
        val github = server { out, _, from -> send(out, from, chunk = 2_000, pauseMs = 100) }
        val mirror = server { out, _, from -> send(out, from) }
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
        val url = server { out, request, from ->
            if (request == 0) send(out, from, honourRange = false, upTo = 100_000) else send(out, from, honourRange = false)
        }
        download(url)
        assertArrayEquals(apk, target.readBytes())
    }

    @Test(timeout = 20_000)
    fun connectionCutShortIsResumed() {
        val url = server { out, request, from ->
            if (request == 0) {
                head(out, "200 OK", "Content-Length: ${apk.size}")
                out.write(apk, 0, 50_000)
                out.flush()
                // Обрыв: соединение закрывается раньше обещанной длины.
            } else {
                send(out, from)
            }
        }
        download(url)
        assertArrayEquals(apk, target.readBytes())
    }

    @Test(timeout = 20_000)
    fun wrongChecksumIsAnErrorAndLeavesNoFile() {
        val url = server { out, _, from -> send(out, from) }
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
        val github = server { out, _, _ -> error(out, "404 Not Found") }
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
        val url = server { out, _, _ ->
            // Без длины: файл кончается, когда закрывается соединение.
            head(out, "200 OK")
            out.write(apk)
            out.flush()
        }
        val progress = mutableListOf<DownloadProgress>()
        download(url, size = 0, progress = progress)
        assertArrayEquals(apk, target.readBytes())
        assertEquals(-1f, progress.first().fraction, 0f)
    }

    private fun sha(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
