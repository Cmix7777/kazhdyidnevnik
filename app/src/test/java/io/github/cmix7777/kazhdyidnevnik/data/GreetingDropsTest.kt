package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class GreetingDropsTest {

    private val zone: ZoneId = ZoneOffset.ofHours(4)
    private val today = LocalDate.of(2026, 10, 12)
    private fun at(hour: Int, minute: Int, date: LocalDate = today) =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun titles() {
        assertEquals("Айзат думал о тебе", GreetingDrops.title(Person.AIZAT, 1))
        assertEquals("Настя думала о тебе 3 раза", GreetingDrops.title(Person.NASTYA, 3))
        assertEquals("Айзат думал о тебе 5 раз", GreetingDrops.title(Person.AIZAT, 5))
        assertEquals("Айзат думал о тебе 12 раз", GreetingDrops.title(Person.AIZAT, 12))
        assertEquals("Айзат думал о тебе 22 раза", GreetingDrops.title(Person.AIZAT, 22))
        assertEquals("Капля полетела к Насте", GreetingDrops.sentText(Person.NASTYA))
        assertEquals("Капля полетела к Айзату", GreetingDrops.sentText(Person.AIZAT))
    }

    @Test
    fun subtitles() {
        assertEquals("сегодня в 14:20", GreetingDrops.subtitle(listOf(at(14, 20)), today, zone))
        assertEquals("вчера в 23:05", GreetingDrops.subtitle(listOf(at(23, 5, today.minusDays(1))), today, zone))
        assertEquals("в 9:10, 14:20 и 18:05", GreetingDrops.subtitle(listOf(at(18, 5), at(9, 10), at(14, 20)), today, zone))
        val many = (8..14).map { at(it, 0) }
        assertEquals("последний раз в 14:00", GreetingDrops.subtitle(many, today, zone))
    }

    @Test
    fun unseenAndMarks() {
        val now = at(20, 0)
        val drops = listOf(at(9, 0), at(14, 0), at(19, 0), at(10, 0, today.minusDays(2)))
        // Уже видел всё до 9:30: остаются 14:00 и 19:00, а капля двухдневной давности не показывается.
        assertEquals(listOf(at(14, 0), at(19, 0)), GreetingDrops.unseen(drops, at(9, 30), now))
        assertEquals(at(19, 0), GreetingDrops.seenMark(drops, at(9, 30)))
        assertTrue(GreetingDrops.unseen(drops, at(19, 0), now).isEmpty())
        // Отправленные хранятся двое суток.
        val kept = GreetingDrops.withSent(drops, now)
        assertEquals(listOf(at(9, 0), at(14, 0), at(19, 0), now), kept)
    }

    @Test
    fun dropsTravelInShareCode() {
        val shared = SharedEvents(Person.AIZAT, at(20, 0), emptyList(), drops = listOf(at(9, 10), at(14, 20)))
        val back = PartnerShare.decode(PartnerShare.encode(shared))!!
        assertEquals(listOf(at(9, 10), at(14, 20)), back.drops)
        // Старый код без капель тоже читается.
        val old = PartnerShare.decode(PartnerShare.encode(SharedEvents(Person.NASTYA, 1L, emptyList())))!!
        assertTrue(old.drops.isEmpty())
    }
}
