package io.github.cmix7777.kazhdyidnevnik.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Разбирает страницу расписания timeo.mveu.ru.
 *
 * Таблица `table.crud`: у первой строки каждого дня есть ячейка с rowspan
 * и подписью вида «Понедельник 05.10», дальше идут столбцы
 * номер пары, время, предмет, тема, преподаватель, аудитория.
 */
object TimeoParser {

    private val timeRegex = Regex("""(\d{1,2}):(\d{2})\s*[—–-]\s*(\d{1,2}):(\d{2})""")
    private val dateRegex = Regex("""(\d{1,2})\.(\d{1,2})""")

    fun parse(html: String, expectedWeekStart: LocalDate): List<Lesson> =
        parse(Jsoup.parse(html), expectedWeekStart)

    fun parse(document: Document, expectedWeekStart: LocalDate): List<Lesson> {
        val table = document.selectFirst("table.crud") ?: return emptyList()
        val result = mutableListOf<Lesson>()
        var currentDate: LocalDate? = null

        for (row in table.select("tr")) {
            if (row.hasClass("border")) continue
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.isEmpty()) continue

            var offset = 0
            if (cells[0].hasAttr("rowspan")) {
                currentDate = parseDayLabel(cells[0].text(), expectedWeekStart)
                offset = 1
            }
            val date = currentDate ?: continue
            if (cells.size < offset + 6) continue

            val time = timeRegex.find(cells[offset + 1].text()) ?: continue
            val subject = cells[offset + 2].text().trim()
            if (subject.isEmpty()) continue

            val roomCell = cells[offset + 5]
            val roomText = roomCell.text().trim()
            val online = roomCell.selectFirst("a") != null ||
                roomText.contains("вебинар", ignoreCase = true)

            result += Lesson(
                date = date,
                number = cells[offset].text().trim().toIntOrNull(),
                start = LocalTime.of(time.groupValues[1].toInt(), time.groupValues[2].toInt()),
                end = LocalTime.of(time.groupValues[3].toInt(), time.groupValues[4].toInt()),
                subject = subject,
                topic = cells[offset + 3].text().trim(),
                teacher = cells[offset + 4].text().trim(),
                room = if (online) "Онлайн" else roomText,
                online = online,
            )
        }
        return result.sortedWith(compareBy({ it.date }, { it.start }))
    }

    /**
     * «Понедельник 05.10» -> дата. Года в подписи нет, поэтому берём год,
     * при котором дата ближе всего к ожидаемой неделе (важно на стыке декабря и января).
     */
    fun parseDayLabel(label: String, reference: LocalDate): LocalDate? {
        val match = dateRegex.find(label) ?: return null
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        return (reference.year - 1..reference.year + 1)
            .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
            .minByOrNull { abs(ChronoUnit.DAYS.between(reference, it)) }
    }
}
