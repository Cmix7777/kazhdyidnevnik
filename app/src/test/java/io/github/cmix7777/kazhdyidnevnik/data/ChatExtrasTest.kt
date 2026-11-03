package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** Новое в переписке: реакции, секреты, эффекты, вложения частями, почерк, статус второго человека. */
class ChatExtrasTest {

    private val aizat = Person.AIZAT

    @Test
    fun reactionToggles() {
        var state = ChatState(restored = true)
        state = ChatLogic.react(state, "nastya:3", "❤️")
        assertEquals("❤️", state.myReactions["nastya:3"])
        val revision = state.revision
        // Тот же смайлик второй раз снимает реакцию, другой — заменяет.
        state = ChatLogic.react(state, "nastya:3", "🔥")
        assertEquals("🔥", state.myReactions["nastya:3"])
        state = ChatLogic.react(state, "nastya:3", "🔥")
        assertNull(state.myReactions["nastya:3"])
        assertTrue(state.revision > revision)
        // Реакции едут в голове, а у второго телефона становятся «её реакциями».
        state = ChatLogic.react(state, "nastya:4", "😂")
        val plan = ChatLogic.planUpload(state, aizat, null, null)
        assertEquals(mapOf("nastya:4" to "😂"), plan.head.reactions)
        val merged = ChatLogic.mergeTheirs(ChatState(), plan.head, 7).state
        assertEquals("😂", merged.theirReactions["nastya:4"])
    }

    @Test
    fun secretTextNeverGoesIntoPush() {
        var state = ChatState(restored = true)
        state = ChatLogic.append(state, aizat, "Билеты на субботу", 1000, secret = true)
        val message = state.mine.last()
        assertTrue(message.secret)
        val preview = ChatLogic.preview(message)
        assertEquals("", preview.text)
        assertEquals("x", preview.kind)
        assertEquals("Секрет под невидимыми чернилами", ChatLogic.shortText(ChatLogic.fromPreview("aizat", preview)))
        state = ChatLogic.open(state, message.key)
        assertTrue(message.key in state.opened)
    }

    @Test
    fun effectsByWords() {
        assertEquals("hearts", ChatLogic.detectEffect("Я тебя люблю"))
        assertEquals("night", ChatLogic.detectEffect("Спокойной ночи!"))
        assertEquals("sun", ChatLogic.detectEffect("Доброе утро"))
        assertEquals("fire", ChatLogic.detectEffect("С днём рождения"))
        assertEquals("fire", ChatLogic.detectEffect("Ура!"))
        // «Культура» — не «ура».
        assertNull(ChatLogic.detectEffect("Сегодня культура речи"))
        var state = ChatState(restored = true)
        state = ChatLogic.append(state, aizat, "люблю", 1, effect = "hearts")
        // Свой эффект уже сыграл, повторять не надо.
        assertTrue(state.mine.last().key in state.played)
    }

    @Test
    fun bigFilesGoInParts() {
        assertEquals(1, ChatLogic.partsFor(10))
        assertEquals(1, ChatLogic.partsFor(ChatLogic.FILE_PART_BYTES))
        assertEquals(3, ChatLogic.partsFor(ChatLogic.FILE_PART_BYTES * 2 + 1))
        assertEquals(listOf("abc", "abc-1", "abc-2"), ChatLogic.partIds("abc", 3))
        var state = ChatState(restored = true)
        state = ChatLogic.append(state, aizat, "", 1, voice = ChatVoice("v1", 4000, parts = 3, wave = "123"))
        assertEquals(listOf("v1"), state.pendingFiles)
        // Уже ушедшее голосовое при удалении стирается в облаке всеми частями.
        state = state.copy(pendingFiles = emptyList(), uploaded = 1)
        state = ChatLogic.deleteMine(state, 1)
        assertEquals(listOf("v1", "v1-1", "v1-2"), state.wipeFiles)
        assertEquals("Голосовое", ChatLogic.shortText(ChatMessage(from = "aizat", seq = 2, at = 2, voice = ChatVoice("x", 1))))
    }

    @Test
    fun newFontReplacesOld() {
        var state = ChatState(restored = true)
        state = ChatLogic.newFont(state, "f1")
        assertEquals("f1", state.myFont)
        assertEquals(listOf("f1"), state.pendingFiles)
        // Первый ещё не ушёл: просто заменяется, стирать в облаке нечего.
        state = ChatLogic.newFont(state, "f2")
        assertEquals(listOf("f2"), state.pendingFiles)
        assertTrue(state.wipeFiles.isEmpty())
        state = ChatLogic.newFont(state.copy(pendingFiles = emptyList()), "f3")
        assertEquals(listOf("f2"), state.wipeFiles)
        assertEquals("f3", ChatLogic.planUpload(state, aizat, null, null).head.font)
    }

    @Test
    fun partnerStatusFromSchedule() {
        val lesson = DayItem.LessonItem(Lesson(java.time.LocalDate.of(2026, 11, 2), 1, LocalTime.of(8, 30), LocalTime.of(10, 0), "Математика", "", "", "312", false))
        assertEquals("на паре до 10:00", PartnerNow.line(Person.NASTYA, listOf(lesson), LocalTime.of(9, 0)))
        assertEquals("свободна до 8:30", PartnerNow.line(Person.NASTYA, listOf(lesson), LocalTime.of(7, 0)))
        assertEquals("свободен до 8:30", PartnerNow.line(Person.AIZAT, listOf(lesson), LocalTime.of(7, 0)))
        assertNull(PartnerNow.line(Person.NASTYA, listOf(lesson), LocalTime.of(11, 0)))
        assertFalse(PartnerNow.line(Person.NASTYA, emptyList(), LocalTime.NOON) != null)
    }

    @Test
    fun filesShowTheirName() {
        var state = ChatState(restored = true)
        state = ChatLogic.append(state, aizat, "", 1, file = ChatFile("f9", "отчёт по практике.pdf", 1_200_000, "application/pdf", parts = 6))
        val message = state.mine.last()
        assertEquals("Файл: отчёт по практике.pdf", ChatLogic.shortText(message))
        assertEquals(listOf("f9" to 6), message.files)
        val preview = ChatLogic.preview(message)
        assertEquals("f", preview.kind)
        assertEquals("отчёт по практике.pdf", preview.text)
        assertEquals("Файл: отчёт по практике.pdf", ChatLogic.shortText(ChatLogic.fromPreview("aizat", preview)))
    }
}
