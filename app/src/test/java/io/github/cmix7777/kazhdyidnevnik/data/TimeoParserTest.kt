package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class TimeoParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/timeo/$name")) { "Нет файла $name" }.readText()

    private val week1 = TimeoParser.parse(fixture("week-2026-10-05.html"), LocalDate.of(2026, 10, 5))
    private val week2 = TimeoParser.parse(fixture("week-2026-10-12.html"), LocalDate.of(2026, 10, 12))

    @Test
    fun countsAllLessonsOfTheWeek() {
        assertEquals(15, week1.size)
        assertEquals(13, week2.size)
    }

    @Test
    fun readsTimeSubjectAndNumber() {
        val first = week1.first()
        assertEquals(LocalDate.of(2026, 10, 5), first.date)
        assertEquals(LocalTime.of(10, 0), first.start)
        assertEquals(LocalTime.of(11, 30), first.end)
        assertEquals(2, first.number)
        assertEquals("Тестирование информационных систем", first.subject)
        assertEquals("Преподаватель 1", first.teacher)
    }

    @Test
    fun webinarLinkMeansOnline() {
        val monday = week1.filter { it.date.dayOfWeek == DayOfWeek.MONDAY }
        assertEquals(4, monday.size)
        assertTrue(monday.all { it.online })
        assertTrue(monday.all { it.room == "Онлайн" })
    }

    @Test
    fun webinarWordMeansOnline() {
        val monday = week2.filter { it.date.dayOfWeek == DayOfWeek.MONDAY }
        assertTrue(monday.all { it.online })
    }

    @Test
    fun keepsRoomsAndGym() {
        val tuesday = week1.filter { it.date == LocalDate.of(2026, 10, 6) }
        assertEquals(listOf("406", "206", "Спортзал"), tuesday.map { it.room })
        assertFalse(tuesday.any { it.online })
    }

    @Test
    fun earlySaturdayLesson() {
        val saturday = week1.filter { it.date == LocalDate.of(2026, 10, 10) }
        assertEquals(LocalTime.of(8, 20), saturday.first().start)
        assertEquals("Разработка мобильных и веб приложений", saturday.first().subject)
    }

    @Test
    fun missingDayHasNoLessons() {
        assertTrue(week2.none { it.date.dayOfWeek == DayOfWeek.FRIDAY })
        assertEquals(4, week2.count { it.date == LocalDate.of(2026, 10, 15) })
    }

    @Test
    fun emptyTableGivesNoLessons() {
        val html = """<table class="crud"><tbody><tr><th></th><th></th><th>Время</th></tr></tbody></table>"""
        assertTrue(TimeoParser.parse(html, LocalDate.of(2026, 11, 2)).isEmpty())
    }

    @Test
    fun pageWithoutTableGivesNoLessons() {
        assertTrue(TimeoParser.parse("<html><body>Ошибка</body></html>", LocalDate.of(2026, 11, 2)).isEmpty())
    }

    @Test
    fun dayLabelYearAroundNewYear() {
        val reference = LocalDate.of(2026, 12, 28)
        assertEquals(LocalDate.of(2026, 12, 31), TimeoParser.parseDayLabel("Четверг 31.12", reference))
        assertEquals(LocalDate.of(2027, 1, 2), TimeoParser.parseDayLabel("Суббота 02.01", reference))
    }

    @Test
    fun codecRoundTrip() {
        val week = WeekSchedule(LocalDate.of(2026, 10, 5), week1, 123L)
        assertEquals(week, ScheduleCodec.decode(ScheduleCodec.encode(week)))
    }
}
