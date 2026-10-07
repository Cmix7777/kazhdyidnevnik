package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class ProgressTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val today = LocalDate.of(2026, 10, 15)

    private fun pdd(daysAgo: Long) = progressKey(today.minusDays(daysAgo), PlanKind.PDD) to 25

    @Test
    fun streakCountsConsecutiveDays() {
        val done = mapOf(pdd(0), pdd(1), pdd(2), pdd(4))
        assertEquals(3, ProgressStats.pddStreak(done, today))
    }

    @Test
    fun streakSurvivesIfTodayNotDoneYet() {
        val done = mapOf(pdd(1), pdd(2))
        assertEquals(2, ProgressStats.pddStreak(done, today))
    }

    @Test
    fun noStreak() {
        assertEquals(0, ProgressStats.pddStreak(mapOf(pdd(3)), today))
    }

    @Test
    fun historyEndsWithToday() {
        val history = ProgressStats.pddHistory(mapOf(pdd(0)), today)
        assertEquals(14, history.size)
        assertEquals(true, history.last())
        assertEquals(false, history.first())
    }

    @Test
    fun minutesOnlyForThisWeek() {
        val done = mapOf(
            progressKey(LocalDate.of(2026, 10, 12), PlanKind.QA) to 40,
            progressKey(LocalDate.of(2026, 10, 18), PlanKind.LARAVEL) to 50,
            progressKey(LocalDate.of(2026, 10, 19), PlanKind.QA) to 45,
        )
        assertEquals(90, ProgressStats.minutesInWeek(done, LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun topicSessionsByWeek() {
        val done = mapOf(
            progressKey(LocalDate.of(2026, 10, 13), PlanKind.QA) to 40,
            progressKey(LocalDate.of(2026, 10, 14), PlanKind.QA) to 40,
            progressKey(LocalDate.of(2026, 10, 20), PlanKind.QA) to 40,
        )
        assertEquals(2, ProgressStats.topicSessions(done, PlanKind.QA, 1))
        assertEquals(1, ProgressStats.topicSessions(done, PlanKind.QA, 2))
    }

    @Test
    fun storeRoundTrip() {
        val store = ProgressStore(File(tmp.root, "progress.json"))
        val done = mapOf(pdd(0), progressKey(today, PlanKind.QA) to 40)
        store.save(done)
        assertEquals(done, store.load())
    }

    @Test
    fun brokenKeysAreIgnored() {
        val done = mapOf("мусор" to 5, "2026-10-15|НЕТ" to 5, pdd(0))
        assertEquals(1, ProgressStats.entries(done).size)
    }

    @Test
    fun minutesFormat() {
        assertEquals("45 мин", formatMinutes(45))
        assertEquals("2 ч", formatMinutes(120))
        assertEquals("2 ч 20 мин", formatMinutes(140))
    }
}
