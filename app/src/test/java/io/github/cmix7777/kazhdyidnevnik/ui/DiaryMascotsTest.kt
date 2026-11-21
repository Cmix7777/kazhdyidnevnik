package io.github.cmix7777.kazhdyidnevnik.ui

import io.github.cmix7777.kazhdyidnevnik.data.Person
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiaryMascotsTest {
    @Test fun sameNightKeepsOneRoostAcrossMidnightAndChangesTheNextEvening() {
        val places = mutableSetOf<Visit>()
        val start = LocalDate.of(2026, 12, 31)
        repeat(3) { index ->
            val date = start.plusDays(index.toLong())
            fun roost(hour: Int, next: Boolean = false) = Visit.entries.filter {
                residentFor(Person.NASTYA, (if (next) date.plusDays(1) else date).atTime(hour, 0), it) == Resident.MOTH
            }
            val evening = roost(18).single()
            assertEquals(listOf(evening), roost(5, next = true))
            assertEquals(emptyList<Visit>(), roost(6, next = true))
            assertEquals(emptyList<Visit>(), roost(17))
            places.add(evening)
        }
        assertEquals(setOf(Visit.HOME, Visit.NOTE, Visit.CHAT), places)
    }

    @Test fun eachProfileKeepsItsResidents() {
        val time = LocalDate.of(2026, 11, 20).atTime(12, 0)
        assertEquals(Resident.LIGHT, residentFor(Person.AIZAT, time, Visit.HOME))
        assertEquals(Resident.LIGHT, residentFor(Person.AIZAT, time, Visit.CHAT))
        assertEquals(Resident.INK, residentFor(Person.AIZAT, time, Visit.NOTE))
        assertEquals(Resident.JERBOA, residentFor(Person.NASTYA, time, Visit.WEEK))
        assertFalse(Visit.entries.any { residentFor(Person.NASTYA, time, it) == Resident.LIGHT })
    }

    @Test fun inkAndGeckoAreAlwaysAtHomeWithADifferentCompanion() {
        val start = LocalDate.of(2026, 12, 30).atStartOfDay()
        for (hour in 0L until 96L) {
            val now = start.plusHours(hour)
            assertEquals(listOf(Resident.INK, Resident.LIGHT), homeResidents(Person.AIZAT, now))
            val pair = homeResidents(Person.NASTYA, now)
            assertEquals(Resident.GECKO, pair.first())
            val mothAtHome = residentFor(Person.NASTYA, now, Visit.HOME) == Resident.MOTH
            assertEquals(if (mothAtHome) Resident.MOTH else Resident.JERBOA, pair.last())
        }
    }
}
