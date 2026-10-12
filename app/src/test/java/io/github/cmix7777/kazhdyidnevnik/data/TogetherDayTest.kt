package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class TogetherDayTest {

    private val day = LocalDate.of(2026, 10, 12)

    private fun t(text: String): LocalTime = LocalTime.parse(if (text.length == 4) "0$text" else text)

    private fun lesson(from: String, to: String) =
        DayItem.LessonItem(Lesson(day, 1, t(from), t(to), "Предмет", "", "", "", online = false))

    private fun work(from: String, to: String) = DayItem.WorkItem(WorkShift(DayOfWeek.MONDAY, t(from), t(to)))

    private fun event(kind: EventKind, from: String, to: String, title: String = "") =
        DayItem.EventItem(UserEvent("e-$from", kind, title, day, t(from), t(to)), day)

    private fun hm(minutes: Int) = "%d:%02d".format(minutes / 60, minutes % 60)

    private fun windows(a: List<DayItem>, b: List<DayItem>) =
        TogetherDay.freeTogether(a, b).map { "${hm(it.first)}–${hm(it.second)}" }

    @Test
    fun emptyDayIsOneLongWindow() {
        assertEquals(listOf("8:00–23:00"), windows(emptyList(), emptyList()))
    }

    @Test
    fun windowStartsWhenTheLaterOfTwoFinishes() {
        // Айзат на парах до 13:40, Настя на парах с 13:00 до 18:20: вместе свободны только вечером.
        val aizat = listOf(lesson("8:30", "13:40"))
        val nastya = listOf(lesson("13:00", "18:20"))
        assertEquals(listOf("18:20–23:00"), windows(aizat, nastya))
    }

    @Test
    fun shortGapsAreNotShown() {
        // Между 13:00 и 14:30 полтора часа: это меньше двух, окна нет.
        val aizat = listOf(work("8:00", "13:00"), lesson("14:30", "22:00"))
        assertEquals(emptyList<String>(), windows(aizat, emptyList()))
    }

    @Test
    fun gapOfExactlyTwoHoursCounts() {
        val aizat = listOf(work("8:00", "13:00"), work("15:00", "23:00"))
        assertEquals(listOf("13:00–15:00"), windows(aizat, emptyList()))
    }

    @Test
    fun overlappingBusyTimeIsMerged() {
        val aizat = listOf(work("9:00", "12:00"))
        val nastya = listOf(lesson("11:00", "16:00"), event(EventKind.OTHER, "15:30", "17:00"))
        assertEquals(listOf("17:00–23:00"), windows(aizat, nastya))
    }

    @Test
    fun lateEveningEventDoesNotLeaveAWindowAfterEndOfDay() {
        // Дело через полночь в этот день идёт до 23:59, поэтому после 22:00 окна нет.
        val nastya = listOf(event(EventKind.WORK, "22:00", "2:00"))
        assertEquals(listOf("8:00–22:00"), windows(emptyList(), nastya))
    }

    @Test
    fun kindsAreListedOnce() {
        val items = listOf(lesson("8:30", "10:00"), lesson("10:10", "11:40"), work("13:00", "17:00"), event(EventKind.DRIVING, "18:00", "19:30"))
        assertEquals("пары + работа + вождение", TogetherDay.kinds(items))
    }

    @Test
    fun otherEventIsShownByItsTitle() {
        assertEquals("кино", TogetherDay.kind(event(EventKind.OTHER, "19:00", "21:00", title = "Кино")))
    }
}
