package io.github.cmix7777.kazhdyidnevnik.data

import io.github.cmix7777.kazhdyidnevnik.data.sheet.SheetReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * Расписание Насти: таблица Excel на сайте колледжа УдГУ на весь семестр.
 * Файл скачивается один раз на проверку, из него собираются нужные недели.
 * Последний файл хранится на телефоне, поэтому новые недели видны и без сети.
 */
class CollegeRepository(private val dir: File) : ScheduleSource {

    override suspend fun download(skip: Int, today: LocalDate, timeoutMs: Int): WeekSchedule {
        val table = timetable(timeoutMs)
        val weekStart = weekStartFor(today).plusWeeks(skip.toLong())
        return WeekSchedule(weekStart, table.week(weekStart), System.currentTimeMillis())
    }

    override fun loadCached(weekStart: LocalDate): WeekSchedule? {
        val file = weekFile(weekStart)
        if (!file.exists()) return null
        return runCatching { ScheduleCodec.decode(file.readText()) }.getOrNull()
    }

    override fun loadOrBuild(weekStart: LocalDate): WeekSchedule? {
        loadCached(weekStart)?.let { return it }
        val source = File(dir, SOURCE)
        if (!source.exists()) return null
        val table = runCatching { CollegeParser.parse(SheetReader.read(source.readBytes())) }.getOrNull() ?: return null
        return WeekSchedule(weekStart, table.week(weekStart), source.lastModified())
    }

    override fun save(week: WeekSchedule) {
        dir.mkdirs()
        weekFile(week.weekStart).writeText(ScheduleCodec.encode(week))
    }

    private fun weekFile(weekStart: LocalDate) = File(dir, "week-$weekStart.json")

    /** Таблица семестра. Если её только что скачали (например, для соседней недели), берём ту же. */
    private suspend fun timetable(timeoutMs: Int): CollegeTimetable = withContext(Dispatchers.IO) {
        lock.withLock {
            val cached = memory
            if (cached != null && System.currentTimeMillis() - cached.second < REUSE_MS) {
                return@withLock cached.first
            }
            val url = fileUrl(timeoutMs)
            val bytes = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .ignoreContentType(true)
                .maxBodySize(MAX_BYTES)
                .execute()
                .bodyAsBytes()
            val table = CollegeParser.parse(SheetReader.read(bytes))
            dir.mkdirs()
            File(dir, SOURCE).writeBytes(bytes)
            File(dir, SOURCE_URL).writeText(url)
            memory = table to System.currentTimeMillis()
            table
        }
    }

    /** Ищет ссылку на странице колледжа; если не вышло — последняя известная. */
    private fun fileUrl(timeoutMs: Int): String {
        val known = File(dir, SOURCE_URL).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.startsWith("http") }
        val found = try {
            val page = Jsoup.connect(CollegeLinks.PAGE).userAgent(USER_AGENT).timeout(timeoutMs).get()
            CollegeLinks.find(page.select("a[href]").map { it.text() to it.attr("href") })
        } catch (e: IOException) {
            null
        }
        return found?.let { runCatching { CollegeLinks.normalize(it) }.getOrNull() } ?: known ?: CollegeLinks.DEFAULT_FILE
    }

    private companion object {
        const val SOURCE = "college.xls"
        const val SOURCE_URL = "college-url.txt"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) Kazhdyidnevnik"
        const val MAX_BYTES = 10 * 1024 * 1024
        const val REUSE_MS = 2 * 60 * 1000L

        val lock = Mutex()

        @Volatile
        var memory: Pair<CollegeTimetable, Long>? = null
    }
}
