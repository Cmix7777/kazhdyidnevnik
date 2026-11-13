package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Закреплённые сообщения (у обоих) и поиск по переписке. */
class PinsSearchTest {

    private val aizat = Person.AIZAT
    private val nastya = Person.NASTYA

    private fun chat(vararg texts: String): ChatState {
        var state = ChatState(restored = true)
        texts.forEachIndexed { i, t -> state = ChatLogic.append(state, aizat, t, 1_000L * (i + 1)) }
        return state
    }

    private fun deliver(from: ChatState, sender: Person, to: ChatState): ChatState =
        ChatLogic.mergeTheirs(to, ChatLogic.planUpload(from, sender, null, null).head, 1).state

    @Test
    fun pinnedMessageIsSeenByBothAndLatestComesFirst() {
        var a = chat("адрес: Пушкинская 5", "код домофона 12", "просто так")
        a = ChatLogic.pin(a, a.mine[0], true, 10_000)
        a = ChatLogic.pin(a, a.mine[1], true, 11_000)
        assertEquals(listOf("код домофона 12", "адрес: Пушкинская 5"), ChatLogic.pinned(a).map { it.text })
        assertTrue(ChatLogic.isPinned(a, a.mine[0]))
        assertFalse(ChatLogic.isPinned(a, a.mine[2]))
        val n = deliver(a, aizat, ChatState(restored = true))
        assertEquals(listOf("код домофона 12", "адрес: Пушкинская 5"), ChatLogic.pinned(n).map { it.text })
    }

    @Test
    fun partnerCanUnpinWhatIPinnedAndPinAgain() {
        var a = chat("важное")
        a = ChatLogic.pin(a, a.mine[0], true, 10_000)
        var n = deliver(a, aizat, ChatState(restored = true))
        // У Насти часы отстают на минуту — открепление всё равно сильнее закрепления, которое она видит.
        n = ChatLogic.pin(n, n.theirs[0], false, 5_000)
        assertTrue(ChatLogic.pinned(n).isEmpty())
        a = deliver(n, nastya, a)
        assertTrue("открепила Настя — открепилось и у Айзата", ChatLogic.pinned(a).isEmpty())
        a = ChatLogic.pin(a, a.mine[0], true, 6_000)
        assertEquals(1, ChatLogic.pinned(a).size)
        n = deliver(a, aizat, n)
        assertEquals(1, ChatLogic.pinned(n).size)
    }

    @Test
    fun pinChangesGoToTheCloudAndRepeatsDoNothing() {
        var a = chat("раз")
        val before = a.revision
        a = ChatLogic.pin(a, a.mine[0], true, 10_000)
        assertEquals(before + 1, a.revision)
        assertTrue(ChatLogic.needsHead(a, null, null))
        assertEquals(a, ChatLogic.pin(a, a.mine[0], true, 11_000))
    }

    @Test
    fun deletedPinnedMessageDisappearsFromPins() {
        var a = chat("раз", "два")
        a = ChatLogic.pin(a, a.mine[1], true, 10_000)
        a = ChatLogic.deleteMine(a, a.mine[1].seq)
        assertTrue(ChatLogic.pinned(a).isEmpty())
    }

    @Test
    fun pinsSurviveReinstall() {
        var a = chat("раз")
        a = ChatLogic.pin(a, a.mine[0], true, 10_000)
        val head = ChatLogic.planUpload(a, aizat, null, null).head
        val restored = ChatLogic.restoreMine(ChatState(), head, emptyList(), ChatMark(0, 0, 0))
        assertEquals(listOf("раз"), ChatLogic.pinned(restored).map { it.text })
    }

    @Test
    fun manyPinsAreTrimmedOldestFirst() {
        var a = chat(*Array(ChatLogic.KEEP_PINS + 10) { "сообщение $it" })
        a.mine.forEachIndexed { i, m -> a = ChatLogic.pin(a, m, true, 100_000L + i) }
        assertEquals(ChatLogic.KEEP_PINS, a.myPins.size)
        assertEquals("сообщение ${ChatLogic.KEEP_PINS + 9}", ChatLogic.pinned(a).first().text)
    }

    @Test
    fun searchFindsAllWordsIgnoringCaseAndYo() {
        val a = chat("Купить ЁЛКУ и гирлянду", "ёлка уже стоит", "купить хлеба")
        val hits = ChatLogic.search(a, "купить елку")
        assertEquals(listOf("Купить ЁЛКУ и гирлянду"), hits.map { it.text })
        assertEquals(listOf("ёлка уже стоит", "Купить ЁЛКУ и гирлянду"), ChatLogic.search(a, "ЕЛК").map { it.text })
        assertTrue(ChatLogic.search(a, "   ").isEmpty())
    }

    @Test
    fun searchLooksIntoFilesVoicesAndPartnerButNotSecretsOrGames() {
        var a = chat("обычное")
        a = ChatLogic.append(a, aizat, "", 5_000, file = ChatFile("f1", "Отчёт по практике.docx", 100))
        a = ChatLogic.append(a, aizat, "секретная практика", 6_000, secret = true)
        a = ChatLogic.append(a, aizat, "", 7_000, voice = ChatVoice("v1", 3_000))
        a = ChatLogic.startGame(a, aizat, "g1", 8_000)
        val theirs = ChatMessage(from = nastya.id, seq = 1, at = 9_000, text = "как практика?")
        a = a.copy(theirs = listOf(theirs))
        val hits = ChatLogic.search(a, "практик", voiceText = mapOf("v1" to "расскажу про практику вечером"))
        assertEquals(listOf("как практика?", "расскажу про практику вечером", "Отчёт по практике.docx"), hits.map { it.text })
        assertTrue(ChatLogic.search(a, "крестики").isEmpty())
    }

    @Test
    fun searchIncludesPreviousChatMarkedAsEarlier() {
        val old = ChatMessage(from = nastya.id, seq = 4, at = 500, text = "старый рецепт блинов")
        val a = chat("новые блины").copy(earlier = listOf(old, old))
        val hits = ChatLogic.search(a, "блин")
        assertEquals(listOf(false, true), hits.map { it.earlier })
    }
}
