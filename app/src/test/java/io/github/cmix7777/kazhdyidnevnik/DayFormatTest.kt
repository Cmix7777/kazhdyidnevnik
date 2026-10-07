package io.github.cmix7777.kazhdyidnevnik

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DayFormatTest {

    @Test
    fun formatsWeekdayAndMonthInRussian() {
        assertEquals("Среда, 7 октября", formatDayTitle(LocalDate.of(2026, 10, 7)))
    }

    @Test
    fun formatsFirstDayOfYear() {
        assertEquals("Пятница, 1 января", formatDayTitle(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun formatsPracticeStart() {
        assertEquals("Понедельник, 2 ноября", formatDayTitle(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun weekRangeInsideOneMonth() {
        assertEquals("5 – 11 октября", formatWeekRange(LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun weekRangeAcrossMonthsAndYears() {
        assertEquals("28 сентября – 4 октября", formatWeekRange(LocalDate.of(2026, 9, 28)))
        assertEquals("28 декабря – 3 января", formatWeekRange(LocalDate.of(2026, 12, 28)))
    }

    @Test
    fun fullDateAndTime() {
        assertEquals("2 ноября 2026", formatDate(LocalDate.of(2026, 11, 2)))
        assertEquals("8:20", formatTime(java.time.LocalTime.of(8, 20)))
        assertEquals("13:05", formatTime(java.time.LocalTime.of(13, 5)))
    }

    @Test
    fun shortDayForNotifications() {
        assertEquals("Чт, 8 октября", formatShortDay(LocalDate.of(2026, 10, 8)))
        assertEquals("Вс, 1 ноября", formatShortDay(LocalDate.of(2026, 11, 1)))
    }
}
