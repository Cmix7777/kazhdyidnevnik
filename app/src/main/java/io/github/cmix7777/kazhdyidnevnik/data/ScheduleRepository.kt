package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Скачивает расписание с timeo.mveu.ru и хранит копию на телефоне. */
class ScheduleRepository(
    private val cacheDir: File,
    private val group: String = DEFAULT_GROUP,
) {

    fun url(skip: Int): String = buildString {
        append(BASE_URL)
        append("?group=")
        append(URLEncoder.encode(group, "UTF-8"))
        if (skip != 0) append("&skip=").append(skip)
    }

    /**
     * Скачать неделю с сайта, не сохраняя: 0 — текущая, 1 — следующая, -1 — прошлая.
     * Сохраняет и сравнивает со старой копией [ScheduleSync].
     */
    suspend fun download(
        skip: Int,
        today: LocalDate = LocalDate.now(),
        timeoutMs: Int = 20_000,
    ): WeekSchedule = withContext(Dispatchers.IO) {
        val expectedStart = weekStartFor(today).plusWeeks(skip.toLong())
        val document = Jsoup.connect(url(skip))
            .userAgent(USER_AGENT)
            .timeout(timeoutMs)
            .get()
        WeekSchedule(
            weekStart = expectedStart,
            lessons = TimeoParser.parse(document, expectedStart),
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    fun loadCached(weekStart: LocalDate): WeekSchedule? {
        val file = fileFor(weekStart)
        if (!file.exists()) return null
        return runCatching { ScheduleCodec.decode(file.readText()) }.getOrNull()
    }

    fun save(week: WeekSchedule) {
        cacheDir.mkdirs()
        fileFor(week.weekStart).writeText(ScheduleCodec.encode(week))
    }

    private fun fileFor(weekStart: LocalDate) = File(cacheDir, "week-$weekStart.json")

    companion object {
        const val DEFAULT_GROUP = "ДИС-234/21Б"
        private const val BASE_URL = "https://timeo.mveu.ru/schedule/table"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) Kazhdyidnevnik"
    }
}

/** Понедельник недели, в которую входит дата. */
fun weekStartFor(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
