package io.github.cmix7777.kazhdyidnevnik.data

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * График работы, на котором написаны тесты (первые недели октября 2026). Настоящий график меняется
 * каждую неделю, а тестам нужен неизменный.
 */
object TestSchedules {
    val octoberAizat: List<WorkShift> = listOf(
        WorkShift(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(13, 0)),
        WorkShift(DayOfWeek.WEDNESDAY, LocalTime.of(17, 0), LocalTime.of(22, 0)),
        WorkShift(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(13, 0)),
        WorkShift(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(15, 0)),
        WorkShift(DayOfWeek.SATURDAY, LocalTime.of(13, 0), LocalTime.of(20, 0)),
        WorkShift(DayOfWeek.SUNDAY, LocalTime.of(8, 0), LocalTime.of(14, 0)),
    )
}
