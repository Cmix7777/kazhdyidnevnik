package io.github.cmix7777.kazhdyidnevnik.data

import io.github.cmix7777.kazhdyidnevnik.data.sheet.SheetReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PersonTest {

    private val nastya = Person.NASTYA.profile
    private val friday = LocalDate.of(2026, 10, 9)

    @Test
    fun profiles() {
        assertEquals("Доброе утро, господин Айзат", Person.AIZAT.profile.morningTitle)
        assertEquals("Доброе утро, Анастасия", nastya.morningTitle)
        assertEquals(WeatherAdvice.LEAVE_BEFORE_MIN, Person.AIZAT.profile.leaveBeforeMinutes)
        assertEquals(50L, nastya.leaveBeforeMinutes)
        assertFalse(nastya.hasPlan)
        assertTrue(nastya.deadlines.isEmpty())
        assertEquals(Person.NASTYA, Person.AIZAT.partner)
        assertEquals(Person.NASTYA, Person.fromId("nastya"))
    }

    @Test
    fun nastyaShiftsAreOneOff() {
        assertEquals(listOf(LocalTime.of(16, 30)), buildDay(friday, emptyList(), nastya.shifts).map { it.start })
        assertEquals(
            listOf(LocalTime.of(9, 0), LocalTime.of(18, 0)),
            buildDay(friday.plusDays(1), emptyList(), nastya.shifts).map { it.start },
        )
        assertEquals(LocalTime.of(21, 0), buildDay(friday.plusDays(2), emptyList(), nastya.shifts).single().end)
        // Через неделю смен нет: график разовый.
        assertTrue(buildDay(friday.plusWeeks(1), emptyList(), nastya.shifts).isEmpty())
    }

    @Test
    fun nastyaLeavesFiftyMinutesEarly() {
        val trips = WeatherAdvice.trips(buildDay(friday, emptyList(), nastya.shifts), nastya.leaveBeforeMinutes)
        assertEquals(LocalTime.of(15, 40), trips[0].time)
        assertEquals(LocalTime.of(21, 40), trips[1].time)
    }

    @Test
    fun nastyaMorningSummary() {
        val sheet = SheetReader.read(requireNotNull(javaClass.getResource("/college/2-smena-2026-10-08.xls")).readBytes())
        val monday = LocalDate.of(2026, 10, 12)
        val items = buildDay(monday, CollegeParser.parse(sheet).week(monday), nastya.shifts)
        val text = Summaries.morning(monday, items, emptyList(), null, nastya)
        assertEquals("2 пары, первая в 13:40, ауд. 200а/4.", text)
    }

    @Test
    fun partnerReminders() {
        val date = LocalDate.of(2026, 10, 8)
        assertTrue(ReminderPlanner.forPartnerDay(date, emptyList(), ReminderSettings()).isEmpty())

        val on = ReminderSettings(partner = PartnerAlerts(morning = true, study = true, weather = true))
        val reminders = ReminderPlanner.forPartnerDay(date, emptyList(), on)
        assertEquals(listOf(ReminderKind.MORNING, ReminderKind.WEATHER), reminders.map { it.kind })
        assertTrue(reminders.all { it.partner })

        // Сайты проверяются, даже если свои изменения выключены, а про второго человека — включены.
        val onlyPartnerChanges = ReminderSettings(
            changes = false,
            morning = false,
            blocks = false,
            evening = false,
            weather = false,
            partner = PartnerAlerts(changes = true),
        )
        assertEquals(
            listOf(ReminderKind.CHECK, ReminderKind.CHECK),
            ReminderPlanner.forDay(date, emptyList(), onlyPartnerChanges).map { it.kind },
        )
    }

    @Test
    fun backupKeepsPartnerAlerts() {
        val settings = ReminderSettings(partner = PartnerAlerts(changes = true, weather = true))
        val text = Backup.encode(emptyMap(), settings, LocalDateTime.of(2026, 10, 8, 22, 0))
        assertEquals(settings, Backup.decode(text)?.settings)
    }
}
