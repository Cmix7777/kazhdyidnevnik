package io.github.cmix7777.kazhdyidnevnik.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Смена на работе. */
data class WorkShift(val day: DayOfWeek, val start: LocalTime, val end: LocalTime)

/** График работы Айзата (не в IT). */
object WorkSchedule {
    val default: List<WorkShift> = listOf(
        WorkShift(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(13, 0)),
        WorkShift(DayOfWeek.WEDNESDAY, LocalTime.of(17, 0), LocalTime.of(22, 0)),
        WorkShift(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(13, 0)),
        WorkShift(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(15, 0)),
        WorkShift(DayOfWeek.SATURDAY, LocalTime.of(13, 0), LocalTime.of(20, 0)),
        WorkShift(DayOfWeek.SUNDAY, LocalTime.of(8, 0), LocalTime.of(14, 0)),
    )
}

/** Практика: со 2 по 15 ноября 2026. */
object Practice {
    val start: LocalDate = LocalDate.of(2026, 11, 2)
    val end: LocalDate = LocalDate.of(2026, 11, 15)
    val totalDays: Int = (end.toEpochDay() - start.toEpochDay() + 1).toInt()

    /** Номер дня практики или null, если сегодня не практика. */
    fun dayNumber(date: LocalDate): Int? =
        if (!date.isBefore(start) && !date.isAfter(end)) {
            (date.toEpochDay() - start.toEpochDay() + 1).toInt()
        } else {
            null
        }
}

/** Элемент дня: пара или смена. */
sealed interface DayItem {
    val start: LocalTime
    val end: LocalTime

    data class LessonItem(val lesson: Lesson) : DayItem {
        override val start: LocalTime get() = lesson.start
        override val end: LocalTime get() = lesson.end
    }

    data class WorkItem(val shift: WorkShift) : DayItem {
        override val start: LocalTime get() = shift.start
        override val end: LocalTime get() = shift.end
    }
}

/** Пары и смены за день, по времени начала. */
fun buildDay(date: LocalDate, lessons: List<Lesson>, shifts: List<WorkShift>): List<DayItem> {
    val lessonItems = lessons.filter { it.date == date }.map { DayItem.LessonItem(it) }
    val workItems = shifts.filter { it.day == date.dayOfWeek }.map { DayItem.WorkItem(it) }
    return (lessonItems + workItems).sortedBy { it.start }
}
