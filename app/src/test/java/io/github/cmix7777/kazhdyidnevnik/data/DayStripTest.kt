package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class DayStripTest {

    private val date = LocalDate.of(2026, 10, 12)

    private fun lesson(subject: String, from: String, to: String) = DayItem.LessonItem(
        Lesson(date, 1, LocalTime.parse(from), LocalTime.parse(to), subject, "", "", "312", false),
    )

    private val items = listOf(
        lesson("Математика", "09:00", "10:30"),
        lesson("История", "10:40", "12:10"),
        DayItem.WorkItem(WorkShift(DayOfWeek.MONDAY, LocalTime.of(15, 0), LocalTime.of(20, 0))),
    )

    @Test
    fun currentBlock() {
        val strip = DayStrip.build(items, LocalTime.of(9, 30))!!
        assertEquals("сейчас Математика, ещё 1 ч", strip.info)
        assertTrue(strip.busyNow)
        assertEquals(3, strip.blocks.size)
        assertEquals(DayStrip.Kind.WORK, strip.blocks[2].kind)
        assertEquals(1f, strip.blocks[2].end, 0.0001f)
        assertEquals(30f / 660f, strip.now, 0.001f)
    }

    @Test
    fun betweenBlocks() {
        val strip = DayStrip.build(items, LocalTime.of(12, 30))!!
        assertEquals("дальше работа через 2 ч 30 мин", strip.info)
        assertFalse(strip.busyNow)
    }

    @Test
    fun beforeTheDay() {
        val strip = DayStrip.build(items, LocalTime.of(8, 20))!!
        assertEquals(0f, strip.now, 0.0001f)
        assertEquals("дальше Математика через 40 мин", strip.info)
    }

    @Test
    fun hiddenWhenDayIsOverOrEmpty() {
        assertNull(DayStrip.build(items, LocalTime.of(20, 0)))
        assertNull(DayStrip.build(items, LocalTime.of(23, 0)))
        assertNull(DayStrip.build(emptyList(), LocalTime.of(10, 0)))
    }
}
