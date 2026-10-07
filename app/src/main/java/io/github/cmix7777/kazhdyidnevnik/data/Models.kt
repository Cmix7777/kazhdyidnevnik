package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate
import java.time.LocalTime

/** Одна пара из расписания колледжа. */
data class Lesson(
    val date: LocalDate,
    val number: Int?,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val topic: String,
    val teacher: String,
    val room: String,
    val online: Boolean,
)

/** Расписание одной недели (с понедельника) и время, когда его скачали. */
data class WeekSchedule(
    val weekStart: LocalDate,
    val lessons: List<Lesson>,
    val fetchedAtMillis: Long,
)
