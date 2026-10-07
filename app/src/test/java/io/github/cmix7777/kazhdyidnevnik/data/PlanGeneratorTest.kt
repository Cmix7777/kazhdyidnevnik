package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PlanGeneratorTest {

    private val lessons = TimeoParser.parse(
        requireNotNull(javaClass.getResource("/timeo/week-2026-10-05.html")).readText(),
        LocalDate.of(2026, 10, 5),
    )

    private fun day(date: LocalDate) = buildDay(date, lessons, WorkSchedule.default)

    @Test
    fun wednesdayMorningIsFilledBeforeClasses() {
        val date = LocalDate.of(2026, 10, 7)
        val plan = PlanGenerator.plan(date, day(date))
        assertEquals(DayLoad.NORMAL, PlanGenerator.load(date, day(date)))
        assertEquals(
            listOf(PlanKind.PDD, PlanKind.CSHARP, PlanKind.QA, PlanKind.LARAVEL),
            plan.map { it.kind },
        )
        assertEquals(LocalTime.of(8, 30), plan.first().start)
        // Последний блок заканчивается до выхода на пару в 12:10 (с запасом на дорогу).
        assertTrue(plan.last().end <= LocalTime.of(11, 30))
        assertEquals(140, plan.sumOf { it.minutes })
    }

    @Test
    fun thursdayIsHeavyAndShort() {
        val date = LocalDate.of(2026, 10, 8)
        assertEquals(DayLoad.HEAVY, PlanGenerator.load(date, day(date)))
        val plan = PlanGenerator.plan(date, day(date))
        assertEquals(2, plan.size)
        assertEquals(PlanKind.PDD, plan.first().kind)
        assertTrue(plan.sumOf { it.minutes } <= 90)
        // После физкультуры (до 18:40) и дороги.
        assertTrue(plan.first().start >= LocalTime.of(19, 10))
    }

    @Test
    fun blocksNeverOverlapClassesOrWork() {
        for (offset in 0L..6L) {
            val date = LocalDate.of(2026, 10, 5).plusDays(offset)
            val items = day(date)
            for (block in PlanGenerator.plan(date, items)) {
                for (item in items) {
                    val overlaps = block.start < item.end && item.start < block.end
                    assertFalse("$date ${block.title} пересекается с ${item.start}", overlaps)
                }
                assertTrue(block.end <= LocalTime.of(23, 0))
            }
        }
    }

    @Test
    fun noCsharpAfterDeadline() {
        val date = LocalDate.of(2026, 11, 4)
        val plan = PlanGenerator.plan(date, buildDay(date, emptyList(), WorkSchedule.default))
        assertTrue(plan.none { it.kind == PlanKind.CSHARP })
    }

    @Test
    fun practiceWeekdayIsBusyDuringTheDay() {
        val date = LocalDate.of(2026, 11, 3)
        val plan = PlanGenerator.plan(date, buildDay(date, emptyList(), WorkSchedule.default))
        assertTrue(plan.isNotEmpty())
        assertTrue(plan.all { it.start >= LocalTime.of(17, 30) || it.end <= LocalTime.of(8, 20) })
    }

    @Test
    fun mockExamDays() {
        assertTrue(MockExams.isMockDay(LocalDate.of(2026, 11, 8)))
        assertFalse(MockExams.isMockDay(LocalDate.of(2026, 11, 15)))
        assertTrue(MockExams.isMockDay(LocalDate.of(2026, 11, 29)))
        assertTrue(MockExams.isMockDay(LocalDate.of(2027, 4, 4)))
        assertTrue(MockExams.isMockDay(LocalDate.of(2027, 4, 18)))
        assertFalse(MockExams.isMockDay(LocalDate.of(2027, 4, 25)))
        assertEquals(LocalDate.of(2026, 11, 8), MockExams.next(LocalDate.of(2026, 10, 7)))
    }

    @Test
    fun mockDayPlanHasFourHourExam() {
        val date = LocalDate.of(2026, 11, 8)
        val plan = PlanGenerator.plan(date, buildDay(date, emptyList(), WorkSchedule.default))
        val mock = plan.single { it.kind == PlanKind.MOCK }
        assertEquals(240, mock.minutes)
        assertNotNull(mock.mockTask)
        assertTrue(mock.start >= LocalTime.of(14, 30))
    }

    @Test
    fun differentMocksGetDifferentTasks() {
        val first = MockExams.taskFor(LocalDate.of(2026, 11, 8))
        val second = MockExams.taskFor(LocalDate.of(2026, 11, 29))
        assertFalse(first == second)
    }

    @Test
    fun curriculumWeeks() {
        assertEquals(1, Curriculum.weekNumber(LocalDate.of(2026, 10, 8)))
        assertEquals(1, Curriculum.weekNumber(LocalDate.of(2026, 10, 18)))
        assertEquals(2, Curriculum.weekNumber(LocalDate.of(2026, 10, 19)))
        assertEquals("Основы тестирования", Curriculum.qaTopic(LocalDate.of(2026, 10, 12)).topic.title)
        val afterProgram = Curriculum.qaTopic(LocalDate.of(2026, 10, 12).plusWeeks(12))
        assertTrue(afterProgram.repeat)
        assertEquals(1, afterProgram.topic.number)
    }
}
