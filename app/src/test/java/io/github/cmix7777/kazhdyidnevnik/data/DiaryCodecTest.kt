package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DiaryCodecTest {

    private val day = LocalDate.of(2026, 10, 10)
    private val diary = Diary(
        events = listOf(
            UserEvent(
                id = "e1",
                kind = EventKind.DRIVING,
                title = "Вождение с инструктором",
                date = day,
                start = LocalTime.of(18, 0),
                end = LocalTime.of(19, 30),
                weekly = true,
                skipped = setOf(day.plusWeeks(1)),
                remindBefore = 60,
                note = "у ТЦ",
            ),
        ),
        deadlines = listOf(Deadline(title = "Зачёт по логистике", date = day.plusDays(20), note = "билеты", id = "d1")),
        money = listOf(
            MoneyEntry("m1", day, 7_000_000, income = true, category = "Зарплата", createdAt = 1),
            MoneyEntry("m2", day, 49_990, income = false, category = "Еда", note = "продукты", createdAt = 2),
        ),
        seeded = setOf("shifts-nastya"),
    )

    @Test
    fun roundTrip() {
        assertEquals(diary, DiaryCodec.decode(DiaryCodec.encode(diary)))
        assertNull(DiaryCodec.decode("не json"))
    }

    @Test
    fun brokenEntriesAreSkipped() {
        val text = """
            {"events":[{"id":"x","kind":"SPACE","date":"2026-10-10","start":"9:00","end":"10:00"},
                       {"id":"y","kind":"SPACE","date":"2026-10-10","start":"09:00","end":"10:00"}],
             "money":[{"id":"bad","date":"2026-10-10","amount":0,"income":true,"category":"Другое"}]}
        """.trimIndent()
        val decoded = DiaryCodec.decode(text)!!
        assertEquals(listOf("y"), decoded.events.map { it.id })
        assertEquals(EventKind.OTHER, decoded.events.single().kind)
        assertEquals(emptyList<MoneyEntry>(), decoded.money)
    }

    @Test
    fun backupCarriesDiary() {
        val text = Backup.encode(emptyMap(), ReminderSettings(), LocalDateTime.of(2026, 10, 10, 12, 0), diary)
        assertEquals(diary, Backup.decode(text)?.diary)
    }
}
