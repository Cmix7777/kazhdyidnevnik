package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleDiffTest {

    private val monday = LocalDate.of(2026, 10, 5)
    private val week = TimeoParser.parse(
        requireNotNull(javaClass.getResource("/timeo/week-2026-10-05.html")).readText(),
        monday,
    )

    private val maths = Lesson(
        date = LocalDate.of(2026, 10, 8),
        number = 2,
        start = LocalTime.of(10, 0),
        end = LocalTime.of(11, 30),
        subject = "Математика",
        topic = "",
        teacher = "",
        room = "305",
        online = false,
    )

    @Test
    fun sameScheduleHasNoChanges() {
        assertTrue(ScheduleDiff.diff(week, week, monday).isEmpty())
    }

    @Test
    fun topicAndTeacherChangesAreIgnored() {
        val updated = week.map { it.copy(topic = "Новая тема", teacher = "Другой преподаватель") }
        assertTrue(ScheduleDiff.diff(week, updated, monday).isEmpty())
    }

    @Test
    fun findsChangedRemovedAndAddedLessons() {
        val first = week[0]
        val second = week[1]
        val moved = first.copy(room = "999", online = false)
        val extra = second.copy(number = 9, start = LocalTime.of(18, 30), end = LocalTime.of(20, 0), subject = "Классный час")
        val updated = listOf(moved) + week.drop(2) + extra

        val changes = ScheduleDiff.diff(week, updated, monday)

        assertEquals(3, changes.size)
        assertTrue(changes.any { it is ScheduleChange.Changed && it.old == first && it.lesson == moved })
        assertTrue(changes.any { it is ScheduleChange.Removed && it.lesson == second })
        assertTrue(changes.any { it is ScheduleChange.Added && it.lesson == extra })
    }

    @Test
    fun pastDaysAreIgnored() {
        val withoutMonday = week.filter { it.date != monday }
        assertTrue(ScheduleDiff.diff(week, withoutMonday, monday.plusDays(1)).isEmpty())
        assertEquals(4, ScheduleDiff.diff(week, withoutMonday, monday).size)
    }

    @Test
    fun describesEachKindOfChange() {
        assertEquals(
            "Чт, 8 октября: добавлена 2 пара в 10:00 — Математика, ауд. 305",
            ScheduleDiff.describe(ScheduleChange.Added(maths)),
        )
        assertEquals(
            "Чт, 8 октября: отменена 2 пара в 10:00 — Математика",
            ScheduleDiff.describe(ScheduleChange.Removed(maths)),
        )
        assertEquals(
            "Чт, 8 октября, 2 пара, Математика: ауд. 305 → ауд. 210",
            ScheduleDiff.describe(ScheduleChange.Changed(maths, maths.copy(room = "210"))),
        )
        val english = maths.copy(subject = "Английский язык", room = "Онлайн", online = true)
        assertEquals(
            "Чт, 8 октября, 2 пара: Математика → Английский язык, ауд. 305 → онлайн",
            ScheduleDiff.describe(ScheduleChange.Changed(maths, english)),
        )
        val later = maths.copy(start = LocalTime.of(11, 40), end = LocalTime.of(13, 10))
        assertEquals(
            "Чт, 8 октября, 2 пара, Математика: 10:00–11:30 → 11:40–13:10",
            ScheduleDiff.describe(ScheduleChange.Changed(maths, later)),
        )
    }
}
