package io.github.cmix7777.kazhdyidnevnik

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val dayNames = mapOf(
    DayOfWeek.MONDAY to "Понедельник",
    DayOfWeek.TUESDAY to "Вторник",
    DayOfWeek.WEDNESDAY to "Среда",
    DayOfWeek.THURSDAY to "Четверг",
    DayOfWeek.FRIDAY to "Пятница",
    DayOfWeek.SATURDAY to "Суббота",
    DayOfWeek.SUNDAY to "Воскресенье",
)

private val monthNames = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

/** Заголовок дня, например «Среда, 7 октября». */
fun formatDayTitle(date: LocalDate): String =
    "${dayNames.getValue(date.dayOfWeek)}, ${date.dayOfMonth} ${monthNames[date.monthValue - 1]}"

/** «2 ноября 2026». */
fun formatDate(date: LocalDate): String =
    "${date.dayOfMonth} ${monthNames[date.monthValue - 1]} ${date.year}"

/** Подпись недели: «5 – 11 октября» или «28 сентября – 4 октября». */
fun formatWeekRange(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    return if (monday.month == sunday.month) {
        "${monday.dayOfMonth} – ${sunday.dayOfMonth} ${monthNames[sunday.monthValue - 1]}"
    } else {
        "${monday.dayOfMonth} ${monthNames[monday.monthValue - 1]} – " +
            "${sunday.dayOfMonth} ${monthNames[sunday.monthValue - 1]}"
    }
}

/** Время как «8:20» или «13:50». */
fun formatTime(time: LocalTime): String = "%d:%02d".format(time.hour, time.minute)
