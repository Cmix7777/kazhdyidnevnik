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
}
