package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RemindersTest {

    private val lessons = TimeoParser.parse(
        requireNotNull(javaClass.getResource("/timeo/week-2026-10-05.html")).readText(),
        LocalDate.of(2026, 10, 5),
    )

    private fun day(date: LocalDate) = buildDay(date, lessons, WorkSchedule.default)

    private val wednesday = LocalDate.of(2026, 10, 7)
    private val thursday = LocalDate.of(2026, 10, 8)

    @Test
    fun planStartsOnFirstDay() {
        assertTrue(PlanGenerator.planFor(wednesday, day(wednesday)).isEmpty())
        assertFalse(PlanGenerator.planFor(thursday, day(thursday)).isEmpty())
    }

    @Test
    fun remindersOfOrdinaryDay() {
        val plan = PlanGenerator.plan(wednesday, day(wednesday))
        val reminders = ReminderPlanner.forDay(wednesday, plan, ReminderSettings())
        assertEquals(
            listOf(
                ReminderKind.CHECK,
                ReminderKind.MORNING,
                ReminderKind.BLOCK,
                ReminderKind.BLOCK,
                ReminderKind.BLOCK,
                ReminderKind.BLOCK,
                ReminderKind.EVENING,
                ReminderKind.CHECK,
            ),
            reminders.map { it.kind },
        )
        assertEquals(LocalTime.of(6, 30), reminders.first().at.toLocalTime())
        assertEquals(LocalTime.of(7, 45), reminders[1].at.toLocalTime())
        assertEquals(plan.map { it.start }, reminders.filter { it.kind == ReminderKind.BLOCK }.map { it.at.toLocalTime() })
        assertEquals(LocalTime.of(22, 15), reminders.single { it.kind == ReminderKind.EVENING }.at.toLocalTime())
        assertTrue(reminders.all { it.at.toLocalDate() == wednesday })
    }

    @Test
    fun eveningWaitsForLateBlock() {
        val block = PlanGenerator.plan(wednesday, day(wednesday)).first()
            .copy(start = LocalTime.of(22, 0), end = LocalTime.of(22, 40))
        val evening = ReminderPlanner.forDay(wednesday, listOf(block), ReminderSettings())
            .single { it.kind == ReminderKind.EVENING }
        assertEquals(LocalTime.of(22, 45), evening.at.toLocalTime())
    }

    @Test
    fun nothingWhenEverythingIsOff() {
        val plan = PlanGenerator.plan(wednesday, day(wednesday))
        val off = ReminderSettings(changes = false, morning = false, blocks = false, evening = false)
        assertTrue(ReminderPlanner.forDay(wednesday, plan, off).isEmpty())
    }

    @Test
    fun noEveningWithoutPlan() {
        val reminders = ReminderPlanner.forDay(wednesday, emptyList(), ReminderSettings())
        assertTrue(reminders.none { it.kind == ReminderKind.EVENING })
    }

    @Test
    fun morningSummaryMentionsWorkAndStudy() {
        val items = day(thursday)
        val text = Summaries.morning(thursday, items, PlanGenerator.planFor(thursday, items))
        assertTrue(text, text.contains("Работа 8:00–13:00."))
        assertTrue(text, text.contains("Учёба: "))
        assertTrue(text, text.contains(", первая в "))
    }

    @Test
    fun morningSummaryWithoutSchedule() {
        val text = Summaries.morning(thursday, null, emptyList())
        assertTrue(text, text.contains("не загружено"))
    }

    @Test
    fun morningSummaryWarnsAboutCloseDeadline() {
        val date = LocalDate.of(2026, 10, 28)
        val text = Summaries.morning(date, emptyList(), emptyList())
        assertTrue(text, text.contains("Сдача сайта на C#: через 3 дня (дата примерная)."))
        assertTrue(text, text.contains("Пар нет."))
    }

    @Test
    fun eveningListsWhatIsLeft() {
        val plan = PlanGenerator.planFor(thursday, day(thursday))
        val text = Summaries.evening(thursday, plan, emptyMap())
        assertTrue(text.orEmpty(), text.orEmpty().startsWith("Отмечено 0 из ${plan.size}."))
        assertTrue(text.orEmpty(), text.orEmpty().contains("Билеты ПДД сегодня ещё не решены."))

        val allDone = plan.associate { it.key(thursday) to it.minutes }
        assertNull(Summaries.evening(thursday, plan, allDone))
    }

    @Test
    fun eveningMentionsStreak() {
        val plan = PlanGenerator.planFor(thursday, day(thursday))
        val done = mapOf(
            progressKey(thursday.minusDays(1), PlanKind.PDD) to 25,
            progressKey(thursday.minusDays(2), PlanKind.PDD) to 25,
        )
        val text = Summaries.evening(thursday, plan, done).orEmpty()
        assertTrue(text, text.contains("серия 2 дня прервётся"))
    }
}
