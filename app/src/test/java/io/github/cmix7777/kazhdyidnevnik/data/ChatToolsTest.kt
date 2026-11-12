package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Правка сообщений, сообщения «в будущее» и общий список. */
class ChatToolsTest {

    private val aizat = Person.AIZAT

    private fun withMessages(vararg texts: String): ChatState {
        var state = ChatState(restored = true)
        texts.forEachIndexed { i, text -> state = ChatLogic.append(state, aizat, text, 1_000L * (i + 1)) }
        return state
    }

    @Test
    fun ownRecentMessageCanBeEditedAndPartnerSeesTheNewText() {
        val state = withMessages("привет", "как делаа?")
        val message = state.mine[1]
        assertTrue(ChatLogic.canEdit(state, message))
        val edited = ChatLogic.editMine(state, message.seq, "  как дела?  ", 50_000)
        assertEquals("как дела?", edited.mine[1].text)
        assertEquals(50_000L, edited.mine[1].edited)
        assertEquals(state.revision + 1, edited.revision)
        assertTrue(ChatLogic.needsHead(edited, null, null))
        // Второй телефон получает голову с исправленным текстом: тот же номер и время — просто новый текст.
        val head = ChatLogic.planUpload(edited, aizat, null, null).head
        val partner = ChatLogic.mergeTheirs(ChatState(theirs = state.mine), head.copy(from = "aizat"), 2).state
        assertEquals("как дела?", partner.theirs[1].text)
        assertNotNull(partner.theirs[1].edited)
    }

    @Test
    fun whatCannotBeEdited() {
        var state = withMessages("раз")
        // Пустой текст у обычного сообщения — не правка.
        assertEquals(state, ChatLogic.editMine(state, 1, "   ", 5))
        // Тот же текст — ничего не меняется.
        assertEquals(state, ChatLogic.editMine(state, 1, "раз", 5))
        // Удалённое и чужое не правятся.
        val deleted = ChatLogic.deleteMine(state, 1)
        assertFalse(ChatLogic.canEdit(deleted, deleted.mine[0]))
        assertFalse(ChatLogic.canEdit(state, ChatMessage("nastya", 1, 1_000, "чужое")))
        // Голосовое не правится.
        state = state.copy(mine = state.mine + ChatMessage("aizat", 2, 3_000, voice = ChatVoice("v", 1_000)))
        assertFalse(ChatLogic.canEdit(state, state.mine[1]))
        // Ушедшее в архивную страницу не правится (второй телефон её не перечитывает).
        val old = withMessages("старое", "новое").copy(headStart = 2)
        assertFalse(ChatLogic.canEdit(old, old.mine[0]))
        assertTrue(ChatLogic.canEdit(old, old.mine[1]))
        // Подпись к фото можно стереть: остаётся фото без подписи.
        val photo = ChatLogic.append(ChatState(restored = true), aizat, "подпись", 1, photo = ChatPhoto("p", 10, 10))
        assertEquals("", ChatLogic.editMine(photo, 1, "", 9).mine[0].text)
    }

    @Test
    fun scheduledMessagesWaitAndThenGoInOrder() {
        var state = withMessages("привет")
        state = ChatLogic.schedule(state, "b", "утром", 9_000_000)
        state = ChatLogic.schedule(state, "a", "через час", 3_600_000, replyTo = "aizat:1")
        state = ChatLogic.schedule(state, "c", "   ", 10)
        assertEquals(listOf("a", "b"), state.scheduled.map { it.id })
        // Ещё рано — ничего не уходит.
        assertEquals(state, ChatLogic.releaseDue(state, aizat, 100_000))
        // Пришло время первого: оно становится обычным сообщением с ответом, второе ждёт.
        val first = ChatLogic.releaseDue(state, aizat, 3_600_500)
        assertEquals(listOf("привет", "через час"), first.mine.map { it.text })
        assertEquals("aizat:1", first.mine.last().replyTo)
        assertEquals(listOf("b"), first.scheduled.map { it.id })
        // Телефон проспал оба срока — уходят оба, по порядку.
        val both = ChatLogic.releaseDue(state, aizat, 10_000_000)
        assertEquals(listOf("привет", "через час", "утром"), both.mine.map { it.text })
        assertTrue(both.scheduled.isEmpty())
        // Отменённое не уходит.
        assertTrue(ChatLogic.unschedule(state, "a").scheduled.none { it.id == "a" })
        // Ответ на удалённое уходит без цитаты.
        val gone = ChatLogic.deleteMine(state, 1)
        assertNull(ChatLogic.releaseDue(gone, aizat, 3_600_500).mine.last().replyTo)
    }

    @Test
    fun sharedListMergesBothPhonesByLatestChange() {
        var mine = SharedList.add(emptyList(), "1", "  молоко  ", aizat, 100)
        mine = SharedList.add(mine, "2", "хлеб", aizat, 200)
        mine = SharedList.add(mine, "3", "", aizat, 300)
        assertEquals(listOf("молоко", "хлеб"), mine.map { it.text })
        // Настя купила хлеб позже, а я в это время убрал молоко.
        val theirs = SharedList.toggle(mine, "2", 500)
        mine = SharedList.remove(mine, "1", 400)
        val merged = SharedList.merge(mine, theirs)
        assertEquals(merged, SharedList.merge(theirs, mine))
        assertTrue(merged.first { it.id == "1" }.deleted)
        assertTrue(merged.first { it.id == "2" }.done)
        assertEquals(listOf("хлеб"), SharedList.visible(merged).map { it.text })
        // Убрать сделанное, а надгробия через месяц забываются.
        val cleared = SharedList.clearDone(merged, 600)
        assertTrue(SharedList.visible(cleared).isEmpty())
        assertTrue(SharedList.prune(cleared, 600 + 31L * 24 * 60 * 60 * 1000).isEmpty())
        // Несделанное раньше сделанного.
        val order = SharedList.toggle(SharedList.add(SharedList.add(emptyList(), "a", "первое", aizat, 1), "b", "второе", aizat, 2), "a", 3)
        assertEquals(listOf("второе", "первое"), SharedList.visible(order).map { it.text })
    }

    @Test
    fun sharedListTravelsWithPlansAndOldCodeStillReads() {
        val list = SharedList.add(emptyList(), "1", "купить цветы", aizat, 100)
        val back = PartnerShare.decode(PartnerShare.encode(SharedEvents(aizat, 5L, emptyList(), list = list)))!!
        assertEquals(list, back.list)
        assertTrue(PartnerShare.decode(PartnerShare.encode(SharedEvents(aizat, 5L, emptyList())))!!.list.isEmpty())
    }
}
