package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OvernightAndPrepTest {

    private val saturday = LocalDate.of(2026, 10, 10)
    private val sunday = LocalDate.of(2026, 10, 11)
    private val monday = LocalDate.of(2026, 10, 12)

    private val night = UserEvent(
        id = "n1",
        kind = EventKind.OTHER,
        title = "Проект",
        date = saturday,
        start = LocalTime.of(23, 0),
        end = LocalTime.of(2, 0),
    )

    private val nextWeek = TimeoParser.parse(
        requireNotNull(javaClass.getResource("/timeo/week-2026-10-12.html")).readText(),
        monday,
    )

    @Test
    fun eventThroughMidnightKnowsItsLength() {
        assertTrue(night.crossesMidnight)
        assertEquals(180, night.minutes)
        assertFalse(night.copy(start = LocalTime.of(20, 0), end = LocalTime.of(22, 30)).crossesMidnight)
        assertEquals(150, night.copy(start = LocalTime.of(20, 0), end = LocalTime.of(22, 30)).minutes)
    }

    @Test
    fun nightEventSplitsBetweenTwoDays() {
        val evening = buildDay(saturday, emptyList(), emptyList(), listOf(night)).single() as DayItem.EventItem
        assertFalse(evening.continued)
        assertEquals(LocalTime.of(23, 0), evening.start)
        assertEquals(LocalTime.of(23, 59), evening.end)
        assertEquals(LocalTime.of(2, 0), evening.shownEnd)
        assertEquals(saturday, evening.startDate)

        val morning = buildDay(sunday, emptyList(), emptyList(), listOf(night)).single() as DayItem.EventItem
        assertTrue(morning.continued)
        assertTrue(morning.isContinuation)
        assertEquals(LocalTime.MIDNIGHT, morning.start)
        assertEquals(LocalTime.of(2, 0), morning.end)
        // Открываем дело в тот день, когда оно началось.
        assertEquals(saturday, morning.startDate)

        assertTrue(buildDay(monday, emptyList(), emptyList(), listOf(night)).isEmpty())
    }

    @Test
    fun weeklyNightEventAndSkippedDay() {
        val weekly = night.copy(weekly = true, skipped = setOf(saturday.plusWeeks(1)))
        assertEquals(1, buildDay(saturday.plusWeeks(2), emptyList(), emptyList(), listOf(weekly)).size)
        assertEquals(1, buildDay(sunday.plusWeeks(2), emptyList(), emptyList(), listOf(weekly)).size)
        // Убрали одну субботу — пропали и вечер, и утро после неё.
        assertTrue(buildDay(saturday.plusWeeks(1), emptyList(), emptyList(), listOf(weekly)).isEmpty())
        assertTrue(buildDay(sunday.plusWeeks(1), emptyList(), emptyList(), listOf(weekly)).isEmpty())
    }

    @Test
    fun nightWorkNeedsOnlyTheWayThere() {
        val shift = night.copy(kind = EventKind.WORK, title = "")
        val evening = buildDay(saturday, emptyList(), emptyList(), listOf(shift))
        val trips = WeatherAdvice.trips(evening, LeaveTimes(40, 10))
        // Выход вечером за 10 минут до смены; домой — уже завтра, в сегодняшние поездки не входит.
        assertEquals(listOf("Выход"), trips.map { it.label })
        assertEquals(LocalTime.of(22, 50), trips.single().time)
        // Утром после ночной смены выходить никуда не надо.
        assertTrue(WeatherAdvice.trips(buildDay(sunday, emptyList(), emptyList(), listOf(shift))).isEmpty())
    }

    @Test
    fun nightEventKeepsTheEveningBusyForStudy() {
        val items = buildDay(saturday, emptyList(), emptyList(), listOf(night))
        val free = PlanGenerator.freeWindows(saturday, items)
        assertTrue(free.all { it.end <= 22 * 60 + 20 })
    }

    @Test
    fun drivingTheorySundayIsSeededOnce() {
        val seeded = Diary().withSeeds(Person.AIZAT)
        val theory = seeded.events.single()
        assertEquals(EventKind.DRIVING, theory.kind)
        assertTrue(theory.weekly)
        assertEquals(LocalTime.of(15, 0), theory.start)
        assertEquals(LocalTime.of(19, 0), theory.end)
        assertTrue(theory.occursOn(sunday))
        assertTrue(theory.occursOn(sunday.plusWeeks(5)))
        assertFalse(theory.occursOn(saturday))
        // Повторно не добавляется, а если Айзат удалил — не возвращается.
        assertEquals(seeded, seeded.withSeeds(Person.AIZAT))
        assertTrue(seeded.withoutEvent(theory.id).withSeeds(Person.AIZAT).events.isEmpty())
        // У Насти вождения из профиля нет.
        assertTrue(Diary().withSeeds(Person.NASTYA).events.none { it.kind == EventKind.DRIVING })
    }

    @Test
    fun drivingTheoryIsBusyTimeForThePlan() {
        val events = Diary().withSeeds(Person.AIZAT).events
        val items = Person.AIZAT.dayItems(sunday, emptyList(), isOwner = true, events = events)
        val free = PlanGenerator.freeWindows(sunday, items)
        // Теория 15:00–19:00 плюс дорога: свободно только с 19:30.
        assertTrue(free.none { it.start < 19 * 60 + 30 && it.end > 14 * 60 + 20 })
    }

    @Test
    fun urgentPrepTheEveningBeforeTheCheck() {
        val events = Diary().withSeeds(Person.AIZAT).events
        val items = Person.AIZAT.dayItems(sunday, emptyList(), isOwner = true, events = events)
        val plan = PlanGenerator.planFor(sunday, items)
        // Полный план ещё не начался, остаётся только срочная подготовка.
        assertEquals(1, plan.size)
        val block = plan.single()
        assertEquals(PlanKind.QA, block.kind)
        assertTrue(block.title, block.title.startsWith("Срочно к проверке по тестированию (1/2)"))
        assertEquals(100, block.minutes)
        assertEquals(LocalTime.of(19, 30), block.start)
        assertTrue(block.end <= LocalTime.of(23, 0))
        assertTrue(block.prompt, block.prompt.contains("Основы тестирования"))
        assertTrue(block.prompt.startsWith("Завтра у меня проверка"))
    }

    @Test
    fun urgentPrepInTheMorningOfTheCheck() {
        val items = buildDay(monday, nextWeek, emptyList())
        val plan = PlanGenerator.planFor(monday, items)
        val first = plan.first()
        assertEquals(PlanKind.QA, first.kind)
        assertTrue(first.title, first.title.endsWith("(2/2)"))
        // Окно до первой онлайн-пары в 10:00.
        assertEquals(LocalTime.of(8, 30), first.start)
        assertEquals(70, first.minutes)
        assertTrue(first.end <= LocalTime.of(9, 50))
        assertEquals(1, plan.count { it.kind == PlanKind.QA })
        assertTrue(first.prompt.startsWith("Сегодня у меня проверка"))
        assertTrue(first.prompt.contains("Баг-репорты"))
        // Утром есть и новые темы, и повтор тем вечера (на случай, если вечером не успел).
        assertTrue(first.detail, first.detail.contains("Потом быстро повтори"))
        assertTrue(first.detail, first.detail.contains("Основы тестирования"))
        assertEquals(6, ExamPrep.step(monday)!!.topics.size)
    }

    @Test
    fun ordinaryTestingStartsAfterTheCheck() {
        val tuesday = monday.plusDays(1)
        val plan = PlanGenerator.planFor(tuesday, buildDay(tuesday, nextWeek, emptyList()))
        val qa = plan.single { it.kind == PlanKind.QA }
        assertEquals("Тестирование: Основы тестирования", qa.title)
        assertNull(ExamPrep.step(tuesday))
        assertTrue(PlanGenerator.planFor(saturday, emptyList()).isEmpty())
    }

    @Test
    fun checkIsInTheDeadlines() {
        val check = Deadlines.default.first { it.title == "Проверка по тестированию" }
        assertEquals(ExamPrep.testingCheck, check.date)
        assertEquals(LocalDate.of(2026, 10, 12), check.date)
    }
}
