package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SpecialDatesTest {

    private val today = LocalDate.of(2026, 10, 12)

    private fun birthday(id: String, date: LocalDate, whose: Person? = Person.NASTYA, yearKnown: Boolean = true) =
        Deadline(title = "", date = date, id = id, occasion = Occasion.BIRTHDAY, whose = whose, yearKnown = yearKnown)

    private fun anniversary(id: String, date: LocalDate) = Deadline(title = "", date = date, id = id, occasion = Occasion.ANNIVERSARY)

    @Test
    fun specialDateRepeatsEveryYear() {
        val spring = birthday("b", LocalDate.of(2006, 3, 14))
        assertEquals(LocalDate.of(2027, 3, 14), spring.nextDate(today))
        val todayDate = birthday("t", LocalDate.of(2006, 10, 12))
        assertEquals(today, todayDate.nextDate(today))
        val plain = Deadline(title = "Зачёт", date = LocalDate.of(2026, 9, 1), id = "p")
        assertEquals(LocalDate.of(2026, 9, 1), plain.nextDate(today))
    }

    @Test
    fun leapDayFallsOnFebruary28thInOrdinaryYears() {
        val leap = birthday("l", LocalDate.of(2004, 2, 29))
        assertEquals(LocalDate.of(2027, 2, 28), leap.nextDate(today))
        assertEquals(LocalDate.of(2028, 2, 29), leap.nextDate(LocalDate.of(2028, 1, 1)))
    }

    @Test
    fun yearsAndTitles() {
        val birthday = birthday("b", LocalDate.of(2006, 3, 14))
        assertEquals("исполнится 21", birthday.yearsText(birthday.nextDate(today)))
        assertEquals("День рождения Насти", birthday.shownTitle)
        assertNull(birthday("u", LocalDate.of(2006, 3, 14), yearKnown = false).yearsText(LocalDate.of(2027, 3, 14)))
        val together = anniversary("a", LocalDate.of(2023, 11, 5))
        assertEquals("3 года вместе", together.yearsText(together.nextDate(today)))
        assertEquals("5 лет вместе", together.yearsText(LocalDate.of(2028, 11, 5)))
        assertEquals("Годовщина", together.shownTitle)
    }

    @Test
    fun anniversaryWinsOverBirthdayAndNewYearComesByItself() {
        val dates = listOf(birthday("b", LocalDate.of(2006, 10, 12)), anniversary("a", LocalDate.of(2023, 10, 12)))
        val special = SpecialDates.today(dates, today)!!
        assertEquals(SpecialKind.ANNIVERSARY, special.kind)
        assertEquals("2026-10-12:a", special.key)
        assertEquals(SpecialKind.BIRTHDAY, SpecialDates.today(dates.take(1), today)!!.kind)
        assertNull(SpecialDates.today(dates, today.plusDays(1)))
        assertEquals(SpecialKind.NEW_YEAR, SpecialDates.today(emptyList(), LocalDate.of(2027, 1, 1))!!.kind)
    }

    @Test
    fun partnersDatesAreAddedWithoutDuplicates() {
        val mine = listOf(birthday("b", LocalDate.of(2006, 3, 14)), Deadline(title = "Зачёт", date = today, id = "p"))
        val theirs = listOf(birthday("b", LocalDate.of(2006, 3, 14)), anniversary("a", LocalDate.of(2023, 11, 5)))
        assertEquals(listOf("b", "a"), SpecialDates.merge(mine, theirs).map { it.id })
    }

    @Test
    fun birthdayIsRemindedOnTheDayAndAWeekBefore() {
        val dates = listOf(birthday("b", LocalDate.of(2006, 10, 19)), anniversary("a", LocalDate.of(2023, 10, 19)))
        val weekBefore = ReminderPlanner.forSpecial(today, dates)
        assertEquals(listOf("b" to 7), weekBefore.map { it.eventId to it.daysBefore })
        assertEquals(LocalTime.of(10, 0), weekBefore.single().at.toLocalTime())
        val onTheDay = ReminderPlanner.forSpecial(today.plusDays(7), dates)
        assertEquals(setOf("b", "a"), onTheDay.map { it.eventId }.toSet())
        assertTrue(onTheDay.all { it.daysBefore == 0 && it.at.toLocalTime() == LocalTime.of(9, 0) })
    }

    @Test
    fun occasionSurvivesSavingAndSharingWithoutNotes() {
        val date = birthday("b", LocalDate.of(2006, 3, 14)).copy(note = "подарок: книга")
        val diary = Diary(deadlines = listOf(date, Deadline(title = "Зачёт", date = today, id = "p")))
        assertEquals(diary, DiaryCodec.decode(DiaryCodec.encode(diary)))

        val shared = SharedEvents(Person.AIZAT, 1L, emptyList(), PartnerShare.relevantDates(diary.deadlines))
        val decoded = PartnerShare.decode(PartnerShare.encode(shared))!!
        assertEquals(listOf(date.copy(note = null)), decoded.dates)
    }
}
