package io.github.cmix7777.kazhdyidnevnik.data

import io.github.cmix7777.kazhdyidnevnik.ui.fx.Holidays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HolidaysTest {

    @Test
    fun newYearComesAndGoesByItself() {
        assertNull(Holidays.active(LocalDate.of(2026, 12, 14)))
        assertEquals(2027, Holidays.active(LocalDate.of(2026, 12, 15))?.newYear)
        assertEquals(2027, Holidays.active(LocalDate.of(2027, 1, 14))?.newYear)
        assertNull(Holidays.active(LocalDate.of(2027, 1, 15)))
        assertNull(Holidays.active(LocalDate.of(2026, 11, 18)))
        assertNotNull(Holidays.active(LocalDate.of(2026, 11, 18), preview = true))
    }

    @Test
    fun countdownStopsOnNewYear() {
        val active = Holidays.active(LocalDate.of(2026, 12, 20))!!
        assertEquals(12L, Holidays.daysLeft(LocalDate.of(2026, 12, 20), active))
        assertNull(Holidays.daysLeft(LocalDate.of(2027, 1, 1), active))
    }

    @Test
    fun newYearWishesGetSalute() {
        assertEquals("fire", ChatLogic.detectEffect("С наступающим!"))
        assertEquals("fire", ChatLogic.detectEffect("с новым годом, любимая"))
    }
}
