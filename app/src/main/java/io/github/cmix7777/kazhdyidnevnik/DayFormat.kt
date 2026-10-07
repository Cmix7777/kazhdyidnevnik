package io.github.cmix7777.kazhdyidnevnik

import java.time.DayOfWeek
import java.time.LocalDate

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
