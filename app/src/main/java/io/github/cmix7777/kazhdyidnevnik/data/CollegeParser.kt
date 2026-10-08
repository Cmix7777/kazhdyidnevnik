package io.github.cmix7777.kazhdyidnevnik.data

import io.github.cmix7777.kazhdyidnevnik.data.sheet.Grid
import io.github.cmix7777.kazhdyidnevnik.data.sheet.SheetFormatException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/**
 * Пара из семестрового расписания колледжа: повторяется каждую неделю в свой день.
 * [onlyOn] — только в эти даты, [exceptOn] — кроме этих дат, [from] — начиная с даты.
 */
data class TemplateLesson(
    val day: DayOfWeek,
    val number: Int?,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val teacher: String,
    val room: String,
    val note: String,
    val onlyOn: List<MonthDay> = emptyList(),
    val exceptOn: List<MonthDay> = emptyList(),
    val from: MonthDay? = null,
) {
    fun occursOn(date: LocalDate): Boolean {
        if (date.dayOfWeek != day) return false
        val monthDay = MonthDay.from(date)
        if (onlyOn.isNotEmpty() && monthDay !in onlyOn) return false
        if (monthDay in exceptOn) return false
        if (from != null && date.isBefore(nearest(from, date))) return false
        return true
    }

    private fun nearest(monthDay: MonthDay, date: LocalDate): LocalDate =
        (date.year - 1..date.year + 1)
            .map { monthDay.atYear(it) }
            .minBy { kotlin.math.abs(it.toEpochDay() - date.toEpochDay()) }
}

/** Семестровое расписание одной группы. */
data class CollegeTimetable(val group: String, val lessons: List<TemplateLesson>) {

    /** Пары недели, начинающейся с понедельника [weekStart]. */
    fun week(weekStart: LocalDate): List<Lesson> =
        lessons.mapNotNull { template ->
            val date = weekStart.plusDays((template.day.value - 1).toLong())
            if (!template.occursOn(date)) return@mapNotNull null
            Lesson(
                date = date,
                number = template.number,
                start = template.start,
                end = template.end,
                subject = template.subject,
                topic = "",
                teacher = template.teacher,
                room = template.room,
                online = false,
                note = template.note,
            )
        }.sortedWith(compareBy({ it.date }, { it.start }))
}

/**
 * Разбирает таблицу «2 смена ЮР, ПД, ТД» с сайта колледжа УдГУ.
 * Строки — пары по дням недели (время во втором столбце), столбцы — группы.
 */
object CollegeParser {

    /** Группа Насти: последний столбец таблицы. */
    const val GROUP = "ТОРГОВОЕ ДЕЛО"

    fun parse(grid: Grid, group: String = GROUP): CollegeTimetable {
        val timeCol = findTimeColumn(grid) ?: throw SheetFormatException("в таблице не нашлось времени пар")
        val firstTimeRow = (0..grid.lastRow).first { TIME_RANGE.containsMatchIn(grid.text(it, timeCol).orEmpty()) }
        val dayCol = findDayColumn(grid, timeCol)
        val (groupCol, groupTitle) = findGroupColumn(grid, group, firstTimeRow)
            ?: throw SheetFormatException("в таблице нет столбца «${group.lowercase()}»")

        data class Slot(val day: DayOfWeek, val start: LocalTime, val end: LocalTime, val rows: IntRange)

        val slots = mutableListOf<Slot>()
        var day = -1
        var previousStart: LocalTime? = null
        var row = firstTimeRow
        while (row <= grid.lastRow) {
            val time = TIME_RANGE.find(grid.text(row, timeCol).orEmpty())
            val merge = grid.mergeAt(row, timeCol)
            val rows = if (merge != null && merge.firstRow == row) merge.firstRow..merge.lastRow else row..row
            if (time != null) {
                val start = time(time.groupValues[1], time.groupValues[2])
                val end = time(time.groupValues[3], time.groupValues[4])
                if (start != null && end != null && end.isAfter(start)) {
                    // Новый день: подпись в столбце дней или время снова с утра.
                    val label = if (dayCol >= 0) rows.firstNotNullOfOrNull { dayOf(grid.value(it, dayCol)) } else null
                    val previous = previousStart
                    var next = when {
                        previous == null -> 0
                        !start.isAfter(previous) -> day + 1
                        else -> day
                    }
                    if (label != null && label.ordinal != day) next = label.ordinal
                    day = next
                    previousStart = start
                    if (day in 0..6) slots += Slot(DayOfWeek.of(day + 1), start, end, rows)
                }
            }
            row = rows.last + 1
        }
        if (slots.isEmpty()) throw SheetFormatException("в таблице не нашлось пар")

        val numbers = slots.map { it.start }.distinct().sorted()
        val lessons = mutableListOf<TemplateLesson>()
        for (slot in slots) {
            val texts = cellTexts(grid, slot.rows, groupCol)
            val entries = texts.mapNotNull { parseEntry(it) }
            if (entries.isEmpty()) continue
            val number = numbers.indexOf(slot.start) + 1
            val dated = entries.filter { it.onlyOn.isNotEmpty() }
            val alternatives = entries.size == 2 && dated.isEmpty()
            entries.forEachIndexed { index, entry ->
                // Обычная пара не идёт в те даты, когда в этой же клетке стоит другая.
                val except = if (entry.onlyOn.isEmpty()) entry.exceptOn + dated.flatMap { it.onlyOn } else entry.exceptOn
                val week = when {
                    !alternatives -> null
                    index == 0 -> "по числителю"
                    else -> "по знаменателю"
                }
                val start = entry.startAt ?: slot.start
                val shift = Duration.between(slot.start, start)
                val end = entry.endAt?.takeIf { it.isAfter(start) } ?: slot.end.plus(shift)
                lessons += TemplateLesson(
                    day = slot.day,
                    number = number,
                    start = start,
                    end = end,
                    subject = entry.subject,
                    teacher = entry.teacher,
                    room = entry.room,
                    note = listOfNotNull(week, entry.note.takeIf { it.isNotBlank() }).joinToString(", "),
                    onlyOn = entry.onlyOn,
                    exceptOn = except.distinct(),
                    from = entry.from,
                )
            }
        }
        return CollegeTimetable(group = groupTitle, lessons = lessons)
    }

    /** Разобранный текст клетки: предмет, преподаватель, аудитория, пометки и даты. */
    data class Entry(
        val subject: String,
        val teacher: String,
        val room: String,
        val note: String,
        val onlyOn: List<MonthDay> = emptyList(),
        val exceptOn: List<MonthDay> = emptyList(),
        val from: MonthDay? = null,
        val startAt: LocalTime? = null,
        val endAt: LocalTime? = null,
    )

    fun parseEntry(raw: String): Entry? {
        var rest = normalize(raw)
        if (rest.none { it.isLetter() }) return null

        var onlyOn = emptyList<MonthDay>()
        var exceptOn = emptyList<MonthDay>()
        var from: MonthDay? = null
        val fromMatch = FROM.find(rest)
        if (fromMatch != null) {
            from = dates(fromMatch.groupValues[1]).firstOrNull()
            rest = rest.substring(fromMatch.range.last + 1).trim()
        } else {
            val exceptMatch = EXCEPT.find(rest)
            val onlyMatch = if (exceptMatch == null) ONLY.find(rest) else null
            if (exceptMatch != null) {
                exceptOn = dates(exceptMatch.groupValues[1])
                rest = rest.substring(exceptMatch.range.last + 1).trim()
            } else if (onlyMatch != null) {
                onlyOn = dates(onlyMatch.groupValues[1])
                rest = rest.substring(onlyMatch.range.last + 1).trim()
            }
        }
        var startAt: LocalTime? = null
        AT_TIME.find(rest)?.let { match ->
            startAt = time(match.groupValues[1], match.groupValues[2])
            rest = rest.substring(match.range.last + 1).trim()
        }

        var subject: String
        var teacher = ""
        var tail: String
        val teacherMatch = TEACHER.find(rest)
        if (teacherMatch != null) {
            subject = rest.substring(0, teacherMatch.range.first)
            teacher = "${teacherMatch.groupValues[1]} ${teacherMatch.groupValues[2]}.${teacherMatch.groupValues[3]}."
            tail = rest.substring(teacherMatch.range.last + 1)
        } else {
            val roomMatch = ROOM.find(rest)
            subject = if (roomMatch != null) rest.substring(0, roomMatch.range.first) else rest
            tail = if (roomMatch != null) rest.substring(roomMatch.range.first) else ""
        }

        var room = ""
        // Аудитория иногда стоит перед предметом: «229/1 Иностранный язык».
        ROOM_FIRST.find(subject)?.let { match ->
            room = match.groupValues[1]
            subject = subject.substring(match.range.last + 1)
        }
        var note = tail
        if (room.isEmpty()) {
            val roomMatch = ROOM.find(tail)
            if (roomMatch != null) {
                room = roomMatch.groupValues[1]
                note = tail.removeRange(roomMatch.range)
            } else if (BUILDING.containsMatchIn(tail)) {
                room = building(clean(tail))
                note = ""
            }
        }
        note = clean(note)
        if (note.startsWith("(") && note.endsWith(")") && note.count { it == '(' } == 1) {
            note = note.substring(1, note.length - 1).trim()
        }
        val endAt = UNTIL.find(note)?.let { time(it.groupValues[1], it.groupValues[2]) }

        subject = clean(subject)
        if (subject.isEmpty()) return null
        return Entry(
            subject = subject,
            teacher = teacher,
            room = room,
            note = note,
            onlyOn = onlyOn,
            exceptOn = exceptOn,
            from = from,
            startAt = startAt,
            endAt = endAt,
        )
    }

    private fun cellTexts(grid: Grid, rows: IntRange, col: Int): List<String> {
        val seen = HashSet<Any>()
        val result = mutableListOf<String>()
        for (row in rows) {
            val merge = grid.mergeAt(row, col)
            val id: Any = merge ?: (row to col)
            if (!seen.add(id)) continue
            val text = if (merge != null) grid.text(merge.firstRow, merge.firstCol) else grid.text(row, col)
            if (!text.isNullOrBlank() && text !in result) result += text
        }
        return result
    }

    private fun findTimeColumn(grid: Grid): Int? =
        (0..minOf(grid.lastCol, 6))
            .map { col -> col to (0..grid.lastRow).count { TIME_RANGE.containsMatchIn(grid.text(it, col).orEmpty()) } }
            .filter { it.second >= 3 }
            .maxByOrNull { it.second }
            ?.first

    private fun findDayColumn(grid: Grid, timeCol: Int): Int =
        (0..minOf(grid.lastCol, timeCol + 1))
            .filter { it != timeCol }
            .map { col -> col to (0..grid.lastRow).count { dayOf(grid.text(it, col)) != null } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first ?: -1

    /** Столбец группы по заголовку (если таких несколько — последний) и сам заголовок. */
    private fun findGroupColumn(grid: Grid, group: String, firstTimeRow: Int): Pair<Int, String>? {
        val wanted = key(group)
        var found: Pair<Int, String>? = null
        for (row in 0 until firstTimeRow) {
            for (col in 0..grid.lastCol) {
                val text = grid.text(row, col) ?: continue
                val current = found
                if (key(text).contains(wanted) && (current == null || col >= current.first)) {
                    found = col to normalize(text)
                }
            }
        }
        return found
    }

    private fun key(text: String): String = normalize(text).uppercase().replace('Ё', 'Е').replace(" ", "")

    private fun normalize(text: String): String = text.replace(' ', ' ').replace(SPACES, " ").trim()

    private fun clean(text: String): String = text.trim().trim('.', ',', ';', ':', '-', '–', ' ').trim()

    private fun building(text: String): String {
        val match = Regex("""^(\d+)\s*корп\.?,?\s*(.*)$""", RegexOption.IGNORE_CASE).find(text) ?: return text
        val place = match.groupValues[2].trim()
        return "корпус ${match.groupValues[1]}" + if (place.isNotEmpty()) ", $place" else ""
    }

    private fun time(hours: String, minutes: String): LocalTime? {
        val h = hours.toIntOrNull() ?: return null
        val m = minutes.toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }

    private fun dates(text: String): List<MonthDay> =
        DATE.findAll(text).mapNotNull { match ->
            runCatching { MonthDay.of(match.groupValues[2].toInt(), match.groupValues[1].toInt()) }.getOrNull()
        }.toList()

    private fun dayOf(text: String?): DayOfWeek? {
        val word = text?.trim()?.lowercase() ?: return null
        return when {
            word.startsWith("пон") -> DayOfWeek.MONDAY
            word.startsWith("вт") -> DayOfWeek.TUESDAY
            word.startsWith("ср") -> DayOfWeek.WEDNESDAY
            word.startsWith("чет") || word == "чт" -> DayOfWeek.THURSDAY
            word.startsWith("пят") || word == "пт" -> DayOfWeek.FRIDAY
            word.startsWith("суб") || word == "сб" -> DayOfWeek.SATURDAY
            word.startsWith("воскр") || word == "вс" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private val SPACES = Regex("""\s+""")
    private val TIME_RANGE = Regex("""(\d{1,2})[.:](\d{2})\s*[-–—]\s*(\d{1,2})[.:](\d{2})""")
    private const val DATE_PATTERN = """\d{1,2}\.\d{1,2}(?:\.(?:\d{4}|\d{2})(?!\d))?\.?"""
    private val DATE = Regex("""(\d{1,2})\.(\d{1,2})""")
    private val FROM = Regex("""^с\s+($DATE_PATTERN)\s*""", RegexOption.IGNORE_CASE)
    private val EXCEPT = Regex("""^($DATE_PATTERN(?:\s*,\s*$DATE_PATTERN)*)\s*не\s+бу?дет\s*""", RegexOption.IGNORE_CASE)
    private val ONLY = Regex("""^($DATE_PATTERN(?:\s*,\s*$DATE_PATTERN)*)\s*""")
    private val AT_TIME = Regex("""^в\s+(\d{1,2})[.:](\d{2})\s*""", RegexOption.IGNORE_CASE)
    private val UNTIL = Regex("""до\s+(\d{1,2})[.:](\d{2})""", RegexOption.IGNORE_CASE)
    private val TEACHER = Regex("""([А-ЯЁ][а-яё]+(?:-[А-ЯЁ][а-яё]+)?)\s*([А-ЯЁ])\.\s?([А-ЯЁ])(?:\.|(?=[\s\d]|$))""")
    private val ROOM = Regex("""(?<![\d/])(\d{1,3}[а-яА-Яa-zA-Z]?/\d{1,2})(?![\d/])""")
    private val ROOM_FIRST = Regex("""^\s*(\d{1,3}[а-яА-Яa-zA-Z]?/\d{1,2})(?![\d/])""")
    private val BUILDING = Regex("""корп|зал""", RegexOption.IGNORE_CASE)
}
