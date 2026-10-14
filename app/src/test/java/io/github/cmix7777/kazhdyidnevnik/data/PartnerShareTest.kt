package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PartnerShareTest {

    private val today = LocalDate.of(2026, 10, 12)

    private val weekly = UserEvent(
        id = "w1",
        kind = EventKind.DRIVING,
        title = "Вождение",
        date = LocalDate.of(2026, 10, 3),
        start = LocalTime.of(18, 0),
        end = LocalTime.of(19, 30),
        weekly = true,
        skipped = setOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 17)),
        remindBefore = 60,
        note = "личная заметка",
    )
    private val future = UserEvent(
        id = "f1",
        kind = EventKind.WORK,
        title = "",
        date = today.plusDays(2),
        start = LocalTime.of(23, 0),
        end = LocalTime.of(2, 0),
        note = "не для всех",
    )
    private val old = future.copy(id = "o1", date = today.minusDays(5))

    @Test
    fun onlyRelevantEventsWithoutPrivateFields() {
        val sent = PartnerShare.relevant(listOf(weekly, future, old), today)
        assertEquals(listOf("w1", "f1"), sent.map { it.id })
        assertTrue(sent.all { it.note.isEmpty() && it.remindBefore == null })
        // Прошлые даты «убрано» не передаются, будущие остаются.
        assertEquals(setOf(LocalDate.of(2026, 10, 17)), sent.first().skipped)
    }

    @Test
    fun codeSurvivesBeingInsideAMessage() {
        val shared = SharedEvents(Person.NASTYA, 1_760_000_000_000L, PartnerShare.relevant(listOf(weekly, future), today))
        val message = PartnerShare.message(Person.NASTYA, shared)
        assertTrue(message, message.contains("Настя"))
        assertTrue(message.contains(PartnerShare.PREFIX))
        val decoded = PartnerShare.decode("Привет!\n$message\nСпасибо")!!
        assertEquals(Person.NASTYA, decoded.from)
        assertEquals(1_760_000_000_000L, decoded.at)
        assertEquals(shared.events, decoded.events)
        // Ночное дело дошло с тем же концом.
        assertTrue(decoded.events.last().crossesMidnight)
    }

    @Test
    fun garbageGivesNothing() {
        assertNull(PartnerShare.decode(""))
        assertNull(PartnerShare.decode("просто текст без кода"))
        assertNull(PartnerShare.decode("KD1:не-код"))
        assertNull(PartnerShare.decode("KD1:AAAA"))
        val good = PartnerShare.encode(SharedEvents(Person.AIZAT, 1L, emptyList()))
        assertNull(PartnerShare.decode(good.dropLast(6)))
        assertEquals(emptyList<UserEvent>(), PartnerShare.decode(good)!!.events)
    }

    @Test
    fun sharedEventsReplaceHardCodedShiftsOnTheOtherPhone() {
        val friday = LocalDate.of(2026, 10, 9)
        val nastya = Person.NASTYA
        // Пока Настя не поделилась, видны смены из профиля.
        val before = nastya.dayItems(friday, emptyList(), isOwner = false)
        assertTrue(before.single() is DayItem.WorkItem)
        // Поделилась: вместо смен из профиля — её дела, смена не задваивается.
        val herShift = UserEvent("s", EventKind.WORK, "", friday, LocalTime.of(17, 0), LocalTime.of(21, 0))
        val after = nastya.dayItems(friday, emptyList(), isOwner = false, events = listOf(herShift), eventsKnown = true)
        assertEquals(1, after.size)
        assertTrue(after.single() is DayItem.EventItem)
        assertEquals(LocalTime.of(17, 0), after.single().start)
        // Айзат тоже делится сменами как делами: у Насти видна его смена из дел, а не из профиля.
        val aizatSunday = LocalDate.of(2026, 10, 18)
        val theory = AizatRegular.drivingTheory
        val hisShift = UserEvent("w", EventKind.WORK, "", aizatSunday, LocalTime.of(8, 0), LocalTime.of(14, 0), weekly = true)
        val seen = Person.AIZAT.dayItems(aizatSunday, emptyList(), isOwner = false, events = listOf(theory, hisShift), eventsKnown = true)
        assertEquals(2, seen.size)
        assertTrue(seen.all { it is DayItem.EventItem })
        // Пока он не поделился, у Насти виден график из приложения.
        val aizatBefore = Person.AIZAT.dayItems(aizatSunday, emptyList(), isOwner = false)
        assertTrue(aizatBefore.single() is DayItem.WorkItem)
    }

    @Test
    fun themeModeFallsBackToDark() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromId(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromId("что-то"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromId("light"))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }
}
