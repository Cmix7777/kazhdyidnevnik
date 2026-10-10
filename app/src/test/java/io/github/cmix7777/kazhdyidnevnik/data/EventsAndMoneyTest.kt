package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EventsAndMoneyTest {

    private val saturday = LocalDate.of(2026, 10, 10)
    private val driving = UserEvent(
        id = "d1",
        kind = EventKind.DRIVING,
        title = "",
        date = saturday,
        start = LocalTime.of(18, 0),
        end = LocalTime.of(19, 30),
        weekly = true,
        remindBefore = 60,
    )

    @Test
    fun weeklyEventsRepeatAndCanSkipADay() {
        assertTrue(driving.occursOn(saturday))
        assertTrue(driving.occursOn(saturday.plusWeeks(3)))
        assertFalse(driving.occursOn(saturday.minusWeeks(1)))
        assertFalse(driving.occursOn(saturday.plusDays(1)))
        val skipped = driving.copy(skipped = setOf(saturday.plusWeeks(1)))
        assertFalse(skipped.occursOn(saturday.plusWeeks(1)))
        assertTrue(skipped.occursOn(saturday.plusWeeks(2)))
        assertEquals("Вождение", driving.displayTitle)
        assertEquals("Права", driving.copy(title = " Права ").displayTitle)
    }

    @Test
    fun eventsJoinTheDay() {
        val items = buildDay(saturday, emptyList(), emptyList(), listOf(driving))
        assertEquals(1, items.size)
        val item = items.single() as DayItem.EventItem
        assertEquals(saturday, item.date)
        assertEquals(LocalTime.of(18, 0), item.start)
        assertFalse(item.isWork)
    }

    @Test
    fun nastyaShiftsBecomeHerOwnEvents() {
        val seeded = Diary().seededWithShifts(Person.NASTYA)
        assertEquals(4, seeded.events.size)
        assertTrue(seeded.events.all { it.kind == EventKind.WORK && !it.weekly })
        // Повторно не добавляются, у Айзата график постоянный — его не трогаем.
        assertEquals(seeded, seeded.seededWithShifts(Person.NASTYA))
        assertEquals(Diary(), Diary().seededWithShifts(Person.AIZAT))

        // На своём телефоне смены — это её дела, на телефоне Айзата — смены из профиля.
        val own = Person.NASTYA.dayItems(saturday, emptyList(), isOwner = true, events = seeded.events)
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(18, 0)), own.map { it.start })
        assertTrue(own.all { it is DayItem.EventItem && it.isWork })
        val partner = Person.NASTYA.dayItems(saturday, emptyList(), isOwner = false, events = seeded.events)
        assertTrue(partner.all { it is DayItem.WorkItem })
        assertEquals(2, partner.size)
    }

    @Test
    fun workEventsCountForTheRoadOthersDoNot() {
        val shift = driving.copy(id = "w", kind = EventKind.WORK, weekly = false, start = LocalTime.of(16, 30), end = LocalTime.of(21, 30))
        val trips = WeatherAdvice.trips(buildDay(saturday, emptyList(), emptyList(), listOf(shift, driving)), 50)
        assertEquals(LocalTime.of(15, 40), trips[0].time)
        assertEquals(LocalTime.of(21, 40), trips[1].time)
        assertTrue(WeatherAdvice.trips(buildDay(saturday, emptyList(), emptyList(), listOf(driving))).isEmpty())
    }

    @Test
    fun eventReminders() {
        val reminders = ReminderPlanner.forEvents(saturday.plusWeeks(1), listOf(driving, driving.copy(id = "x", remindBefore = null)))
        assertEquals(1, reminders.size)
        assertEquals(saturday.plusWeeks(1).atTime(17, 0), reminders[0].at)
        assertEquals(ReminderKind.EVENT, reminders[0].kind)
        assertEquals("d1", reminders[0].eventId)
        assertTrue(ReminderPlanner.forEvents(saturday.plusDays(1), listOf(driving)).isEmpty())
    }

    @Test
    fun balanceByDay() {
        val aug29 = LocalDate.of(2026, 8, 29)
        val entries = listOf(
            MoneyEntry("1", aug29, 7_000_000, income = true, category = "Зарплата"),
            MoneyEntry("2", aug29.plusDays(1), 6_990_000, income = false, category = "Одежда", note = "куртка"),
            MoneyEntry("3", aug29.plusDays(3), 50_000, income = true, category = "Подарок"),
        )
        val days = MoneyStats.balanceByDay(entries, aug29.minusDays(1), aug29.plusDays(3))
        assertEquals(
            listOf(0L, 7_000_000L, 10_000L, 10_000L, 60_000L),
            days.map { it.second },
        )
        assertEquals(60_000L, MoneyStats.balance(entries))
        assertEquals(7_000_000L, MoneyStats.balance(entries, aug29))
        assertEquals(7_050_000L to 6_990_000L, MoneyStats.totals(entries, aug29, aug29.plusDays(5)))
        assertEquals(listOf("Одежда" to 6_990_000L), MoneyStats.expensesByCategory(entries, aug29, aug29.plusDays(5)))
    }

    @Test
    fun moneyText() {
        assertEquals(7_000_000L, MoneyFormat.parse("70 000"))
        assertEquals(9_990L, MoneyFormat.parse("99,90"))
        assertEquals(150L, MoneyFormat.parse("1.5"))
        assertEquals(10_000L, MoneyFormat.parse("100 ₽"))
        assertNull(MoneyFormat.parse("0"))
        assertNull(MoneyFormat.parse("-5"))
        assertNull(MoneyFormat.parse("1,234"))
        assertNull(MoneyFormat.parse("сто"))
        assertNull(MoneyFormat.parse(""))

        assertEquals("70 000 ₽", MoneyFormat.format(7_000_000))
        assertEquals("+100 ₽", MoneyFormat.format(10_000, sign = true))
        assertEquals("−1 200,50 ₽", MoneyFormat.format(-120_050))
        assertEquals("70 тыс.", MoneyFormat.compact(7_000_000))
        assertEquals("1,2 млн", MoneyFormat.compact(120_000_000))
        assertEquals("950", MoneyFormat.compact(95_000))
        assertEquals("99,90", MoneyFormat.forInput(9_990))
        assertEquals("70000", MoneyFormat.forInput(7_000_000))
    }

    @Test
    fun diaryMergeKeepsEverything() {
        val entry = MoneyEntry("m1", saturday, 100, income = false, category = "Еда")
        val mine = Diary(events = listOf(driving), money = listOf(entry))
        val other = Diary(
            events = listOf(driving.copy(title = "другое")),
            deadlines = listOf(Deadline("Зачёт", saturday, id = "z1"), Deadline("Без номера", saturday)),
            money = listOf(entry.copy(id = "m2")),
        )
        val merged = mine.mergeWith(other)
        assertEquals("", merged.events.single().title)
        assertEquals(listOf("z1"), merged.deadlines.map { it.id })
        assertEquals(listOf("m1", "m2"), merged.money.map { it.id })
        assertEquals(4, merged.size)
    }
}
