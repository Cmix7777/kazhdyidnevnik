package io.github.cmix7777.kazhdyidnevnik.live

import io.github.cmix7777.kazhdyidnevnik.data.CollegeLinks
import io.github.cmix7777.kazhdyidnevnik.data.CollegeParser
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleRepository
import io.github.cmix7777.kazhdyidnevnik.data.sheet.SheetReader
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.LocalDate

/**
 * Настоящие сайты расписаний (только на сервере проверки, где есть сеть). Сетевой сбой — не провал:
 * тест пропускается. Провал — если файл скачался, а разобрать его не вышло или разбор идёт слишком долго
 * (на телефоне это выглядело бы как вечно крутящаяся капля).
 */
class LiveSourcesTest {

    private val live = System.getenv("CI") == "true"

    private fun log(text: String) = println("LIVE: $text")

    @Test(timeout = 180_000)
    fun nastyaCollegeFileDownloadsAndParsesQuickly() {
        assumeTrue(live)
        val started = System.nanoTime()
        val url = try {
            val page = Jsoup.connect(CollegeLinks.PAGE).userAgent(AGENT).timeout(25_000).get()
            val found = CollegeLinks.find(page.select("a[href]").map { it.text() to it.attr("href") })
            log("страница колледжа: ${ms(started)} мс, ссылка: $found")
            found?.let { CollegeLinks.normalize(it) } ?: CollegeLinks.DEFAULT_FILE
        } catch (e: IOException) {
            log("страница колледжа недоступна: $e")
            CollegeLinks.DEFAULT_FILE
        }
        val downloadStart = System.nanoTime()
        val bytes = try {
            Jsoup.connect(url).userAgent(AGENT).timeout(40_000).ignoreContentType(true).maxBodySize(10 * 1024 * 1024)
                .execute().bodyAsBytes()
        } catch (e: IOException) {
            log("файл недоступен: $e")
            assumeTrue("сайт колледжа недоступен с сервера проверки", false)
            return
        }
        log("файл $url: ${bytes.size} байт за ${ms(downloadStart)} мс")
        saveCopy("college-live" + (if (url.lowercase().endsWith("x")) ".xlsx" else ".xls"), bytes)

        val readStart = System.nanoTime()
        val grid = SheetReader.read(bytes)
        val biggest = grid.merges.maxOfOrNull { (it.lastRow - it.firstRow + 1).toLong() * (it.lastCol - it.firstCol + 1) } ?: 0
        log("таблица прочитана за ${ms(readStart)} мс: строк ${grid.lastRow + 1}, столбцов ${grid.lastCol + 1}, объединений ${grid.merges.size}, самое большое на $biggest клеток")

        val parseStart = System.nanoTime()
        val table = CollegeParser.parse(grid)
        val monday = weekStartFor(LocalDate.now())
        val week = table.week(monday)
        val next = table.week(monday.plusWeeks(1))
        log("разбор за ${ms(parseStart)} мс: на этой неделе пар ${week.size}, на следующей ${next.size}")
        week.take(12).forEach { log("  ${it.date} ${it.start}-${it.end} ${it.subject} | ${it.room} | ${it.teacher}") }
        assertTrue("разбор файла колледжа занял ${ms(readStart)} мс", ms(readStart) < 5_000)
    }

    @Test(timeout = 120_000)
    fun aizatTimeoSiteAnswersAndParses() {
        assumeTrue(live)
        val dir = Files.createTempDirectory("timeo").toFile()
        val started = System.nanoTime()
        val week = try {
            runBlocking { ScheduleRepository(dir).download(0, LocalDate.now(), 40_000) }
        } catch (e: IOException) {
            log("timeo недоступен: $e")
            assumeTrue("timeo недоступен с сервера проверки", false)
            return
        }
        log("timeo: ${ms(started)} мс, пар на неделе ${week.lessons.size}")
        week.lessons.take(12).forEach { log("  ${it.date} ${it.start}-${it.end} ${it.subject} | ${it.room} | ${it.teacher}") }
    }

    private fun saveCopy(name: String, bytes: ByteArray) {
        val dir = File(System.getProperty("user.dir"), "build/live").apply { mkdirs() }
        File(dir, name).writeBytes(bytes)
    }

    private fun ms(from: Long) = (System.nanoTime() - from) / 1_000_000

    private companion object {
        const val AGENT = "Mozilla/5.0 (Linux; Android 16) Kazhdyidnevnik"
    }
}
