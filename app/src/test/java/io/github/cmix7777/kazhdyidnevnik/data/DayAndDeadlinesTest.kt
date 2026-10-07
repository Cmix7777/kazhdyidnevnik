package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DayAndDeadlinesTest {

    private val lessons = TimeoParser.parse(
        requireNotNull(javaClass.getResource("/timeo/week-2026-10-05.html")).readText(),
        LocalDate.of(2026, 10, 5),
    )

    @Test
    fun tuesdayStartsWithWorkThenLessons() {
        val day = buildDay(LocalDate.of(2026, 10, 6), lessons, WorkSchedule.default)
        assertEquals(4, day.size)
        assertTrue(day.first() is DayItem.WorkItem)
        assertEquals(LocalTime.of(8, 0), day.first().start)
        assertEquals(listOf(8, 13, 15, 17), day.map { it.start.hour })
    }

    @Test
    fun mondayHasNoWork() {
        val day = buildDay(LocalDate.of(2026, 10, 5), lessons, WorkSchedule.default)
        assertTrue(day.all { it is DayItem.LessonItem })
    }

    @Test
    fun sundayIsOnlyWork() {
        val day = buildDay(LocalDate.of(2026, 10, 11), lessons, WorkSchedule.default)
        assertEquals(1, day.size)
        assertTrue(day.single() is DayItem.WorkItem)
    }

    @Test
    fun practiceDays() {
        assertNull(Practice.dayNumber(LocalDate.of(2026, 11, 1)))
        assertEquals(1, Practice.dayNumber(LocalDate.of(2026, 11, 2)))
        assertEquals(14, Practice.dayNumber(LocalDate.of(2026, 11, 15)))
        assertNull(Practice.dayNumber(LocalDate.of(2026, 11, 16)))
        assertEquals(14, Practice.totalDays)
    }

    @Test
    fun russianPluralForDays() {
        assertEquals("1 день", pluralDays(1))
        assertEquals("2 дня", pluralDays(2))
        assertEquals("5 дней", pluralDays(5))
        assertEquals("11 дней", pluralDays(11))
        assertEquals("21 день", pluralDays(21))
        assertEquals("22 дня", pluralDays(22))
        assertEquals("112 дней", pluralDays(112))
    }

    @Test
    fun russianPluralForLessons() {
        assertEquals("1 пара", pluralLessons(1))
        assertEquals("3 пары", pluralLessons(3))
        assertEquals("5 пар", pluralLessons(5))
    }

    @Test
    fun countdown() {
        val today = LocalDate.of(2026, 10, 7)
        assertEquals("через 26 дней", countdownText(daysBetween(today, LocalDate.of(2026, 11, 2))))
        assertEquals("сегодня", countdownText(0))
        assertEquals("3 дня назад", countdownText(-3))
    }
}
