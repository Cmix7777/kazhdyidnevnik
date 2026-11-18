package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitsTest {

    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun streakCountsFromYesterdayWhenTodayIsOpen() {
        var habits = Habits.add(emptyList(), "a", "  Вода  ", 0L)
        assertEquals("Вода", habits.single().title)
        habits = Habits.toggle(habits, "a", today.minusDays(1))
        habits = Habits.toggle(habits, "a", today.minusDays(2))
        assertEquals(2, Habits.streak(habits.single(), today))
        habits = Habits.toggle(habits, "a", today)
        assertEquals(3, Habits.streak(habits.single(), today))
        habits = Habits.toggle(habits, "a", today)
        assertFalse(Habits.isDone(habits.single(), today))
    }

    @Test
    fun onlySharedHabitsLeaveThePhone() {
        var habits = Habits.add(emptyList(), "a", "Вода", 0L)
        habits = Habits.add(habits, "b", "Дневник", 0L)
        habits = Habits.setShared(habits, "b", false)
        habits = Habits.setReminder(habits, "a", 7)
        habits = Habits.toggle(habits, "a", today.minusDays(90))
        habits = Habits.toggle(habits, "a", today)
        val shared = Habits.shareable(habits, today)
        assertEquals(listOf("a"), shared.map { it.id })
        assertEquals(null, shared.single().remindHour)
        assertEquals(listOf(today.toString()), shared.single().done)
    }

    @Test
    fun habitsTravelWithSharedEvents() {
        val habit = Habit("a", "Вода", done = listOf(today.toString()))
        val decoded = PartnerShare.decode(PartnerShare.encode(SharedEvents(Person.NASTYA, 1L, emptyList(), habits = listOf(habit))))
        assertEquals(listOf(habit), decoded?.habits)
    }

    @Test
    fun practiceHidesLessons() {
        val period = PracticePeriod(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 13))
        assertTrue(period.contains(LocalDate.of(2026, 11, 9)))
        assertTrue(period.contains(LocalDate.of(2026, 12, 13)))
        assertFalse(period.contains(LocalDate.of(2026, 12, 14)))
        assertTrue(period.touchesWeek(LocalDate.of(2026, 12, 7)))
        assertFalse(period.touchesWeek(LocalDate.of(2026, 12, 14)))
    }

    @Test
    fun habitsAndListSurviveBackup() {
        val habit = Habit("a", "Вода", remindHour = 7, shared = false, done = listOf(today.toString()))
        val item = ListItem("l", "Хлеб", "nastya", 1L, 1L)
        val text = Backup.encode(emptyMap(), ReminderSettings(), java.time.LocalDateTime.of(2026, 11, 20, 10, 0), habits = listOf(habit), list = listOf(item))
        val back = Backup.decode(text)
        assertEquals(listOf(habit), back?.habits)
        assertEquals(listOf(item), back?.list)
        val merged = Habits.mergeBackup(listOf(habit.copy(done = listOf(today.minusDays(1).toString()))), listOf(habit))
        assertEquals(2, merged.single().done.size)
    }
}
