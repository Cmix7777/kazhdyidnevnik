package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ChatTest {

    private val aizat = Person.AIZAT
    private val zone = ZoneId.of("Europe/Samara")

    private fun theirs(seq: Int, at: Long = seq * 1000L, text: String = "сообщение $seq", replyTo: String? = null) =
        ChatMessage(from = "nastya", seq = seq.toLong(), at = at, text = text, replyTo = replyTo)

    private fun mine(seq: Int, at: Long = seq * 1000L, text: String = "моё $seq") =
        ChatMessage(from = "aizat", seq = seq.toLong(), at = at, text = text)

    private fun withMessages(count: Int): ChatState {
        var state = ChatState(restored = true)
        repeat(count) { state = ChatLogic.append(state, aizat, "сообщение $it", it.toLong()) }
        return state
    }

    @Test
    fun messagesAreNumberedAndNeverGoBackInTime() {
        var state = ChatState(restored = true)
        state = ChatLogic.append(state, aizat, "  Привет  ", now = 1000)
        // Часы телефона отстали: новое сообщение всё равно позже предыдущего.
        state = ChatLogic.append(state, aizat, "Как дела?", now = 900)
        assertEquals(listOf(1L, 2L), state.mine.map { it.seq })
        assertEquals("Привет", state.mine[0].text)
        assertTrue(state.mine[1].at > state.mine[0].at)
        // Ответ на сообщение, пришедшее «из будущего» (часы второго телефона спешат), встаёт после него.
        state = state.copy(theirs = listOf(theirs(1, at = 5000)))
        state = ChatLogic.append(state, aizat, "Отлично", now = 2000, replyTo = "nastya:1")
        assertEquals(5001L, state.mine.last().at)
        assertEquals("nastya:1", state.mine.last().replyTo)
        assertEquals(3L, state.revision)
    }

    @Test
    fun blankMessageIsIgnoredAndLongOneIsCut() {
        assertTrue(ChatLogic.append(ChatState(), aizat, "   \n ", 1).mine.isEmpty())
        val long = ChatLogic.append(ChatState(), aizat, "я".repeat(ChatLogic.MAX_TEXT + 50), 1)
        assertEquals(ChatLogic.MAX_TEXT, long.mine.single().text.length)
        val photo = ChatLogic.append(ChatState(), aizat, "", 1, photo = ChatPhoto("abc", 10, 20))
        assertEquals("abc", photo.mine.single().photo?.id)
        assertEquals(listOf("abc"), photo.pendingFiles)
    }

    @Test
    fun uploadPlanCarriesNewMessagesAndClearsPendingAfterwards() {
        val state = withMessages(3)
        val plan = ChatLogic.planUpload(state, aizat, push = "tok", hook = null)
        assertEquals(3, plan.head.messages.size)
        assertEquals(3L, plan.head.last)
        assertEquals(0, plan.head.pages)
        assertTrue(plan.pages.isEmpty())
        assertEquals("tok", plan.head.push)
        assertTrue(ChatLogic.hasPending(state, "tok", null))
        assertEquals(ChatTick.PENDING, ChatLogic.tick(state, state.mine[0], mine = true))

        val after = ChatLogic.afterUpload(state, plan, version = 77, revision = state.revision)
        assertFalse(ChatLogic.hasPending(after, "tok", null))
        assertEquals(3L, after.uploaded)
        assertEquals(77L, after.myVersion)
        assertEquals(ChatTick.SENT, ChatLogic.tick(after, after.mine[0], mine = true))
        assertEquals(ChatTick.READ, ChatLogic.tick(after.copy(theirRead = 1), after.mine[0], mine = true))
        // Новый токен пушей — повод переписать голову.
        assertTrue(ChatLogic.hasPending(after, "другой", null))

        // Пока шла отправка, написали ещё: оно остаётся неотправленным.
        val during = ChatLogic.append(state, aizat, "ещё", 10)
        val merged = ChatLogic.afterUpload(during, plan, 77, state.revision)
        assertTrue(ChatLogic.hasPending(merged, "tok", null))
        assertEquals(ChatTick.PENDING, ChatLogic.tick(merged, merged.mine.last(), mine = true))
    }

    @Test
    fun crowdedHeadRollsIntoAnArchivePage() {
        val state = withMessages(ChatLogic.HEAD_LIMIT_COUNT + 1)
        val plan = ChatLogic.planUpload(state, aizat, null, null)
        assertEquals(1, plan.pages.size)
        assertEquals(0, plan.pages[0].index)
        assertEquals(ChatLogic.HEAD_LIMIT_COUNT, plan.pages[0].messages.size)
        assertEquals(1L, plan.pages[0].messages.first().seq)
        assertEquals(1, plan.head.messages.size)
        assertEquals(state.mine.last().seq, plan.head.first)
        assertEquals(1, plan.head.pages)

        val after = ChatLogic.afterUpload(state, plan, 1, state.revision)
        assertEquals(listOf(1L), after.pageStarts)
        assertEquals(plan.head.first, after.headStart)
        assertTrue(after.dirtyPages.isEmpty())

        // Следующее сообщение идёт только в голову, архив не трогается.
        val next = ChatLogic.planUpload(ChatLogic.append(after, aizat, "дальше", 10_000), aizat, null, null)
        assertTrue(next.pages.isEmpty())
        assertEquals(2, next.head.messages.size)
        assertEquals(1, next.head.pages)
    }

    @Test
    fun heavyHeadRollsByBytesToo() {
        val state = withMessages(12)
        val plan = ChatLogic.planUpload(state, aizat, null, null, sizeOf = { it.size * 10_000 })
        assertEquals(1, plan.pages.size)
        assertEquals(11, plan.pages[0].messages.size)
        assertEquals(listOf(12L), plan.head.messages.map { it.seq })
        // Одно огромное сообщение в голове не уходит в пустую страницу.
        val single = ChatLogic.planUpload(withMessages(1), aizat, null, null, sizeOf = { 1_000_000 })
        assertTrue(single.pages.isEmpty())
    }

    @Test
    fun deletingArchivedMessageRewritesItsPageAndTellsThePartner() {
        val state = withMessages(ChatLogic.HEAD_LIMIT_COUNT + 1)
        val rolled = ChatLogic.afterUpload(state, ChatLogic.planUpload(state, aizat, null, null), 1, state.revision)
        val deleted = ChatLogic.deleteMine(rolled, 5)
        val gone = deleted.mine.first { it.seq == 5L }
        assertTrue(gone.deleted)
        assertEquals("", gone.text)
        assertEquals(listOf(0), deleted.dirtyPages)
        assertEquals(listOf(5L), deleted.deletedInPages)

        val plan = ChatLogic.planUpload(deleted, aizat, null, null)
        assertEquals(1, plan.pages.size)
        assertTrue(plan.pages[0].messages.first { it.seq == 5L }.deleted)
        assertEquals(listOf(5L), plan.head.deletedInPages)

        // Удаление в голове архив не трогает.
        val inHead = ChatLogic.deleteMine(rolled, rolled.mine.last().seq)
        assertTrue(inHead.dirtyPages.isEmpty())
        assertTrue(ChatLogic.hasPending(inHead, null, null))
        // Чужое или несуществующее сообщение не удаляется.
        assertEquals(rolled, ChatLogic.deleteMine(rolled, 9999))
    }

    @Test
    fun deletingPhotoWipesItInTheCloud() {
        var state = ChatLogic.append(ChatState(restored = true), aizat, "", 1, photo = ChatPhoto("p1", 4, 3))
        // Ещё не ушло: стирать в облаке нечего, просто не отправляем.
        val early = ChatLogic.deleteMine(state, 1)
        assertTrue(early.pendingFiles.isEmpty())
        assertTrue(early.wipeFiles.isEmpty())
        state = state.copy(pendingFiles = emptyList())
        val late = ChatLogic.deleteMine(state, 1)
        assertEquals(listOf("p1"), late.wipeFiles)
        assertNull(late.mine.single().photo)
    }

    @Test
    fun partnerHeadMergesNewMessagesDeletionsAndAsksForMissingPages() {
        val head = ChatHead(from = "nastya", pages = 2, last = 12, messages = (10..12).map { theirs(it) }, deletedInPages = listOf(3))
        val state = ChatState(theirs = (1..5).map { theirs(it) })
        val merge = ChatLogic.mergeTheirs(state, head.copy(push = "их-токен"), version = 99)
        assertEquals(6L, merge.missingFrom)
        assertEquals(99L, merge.state.theirVersion)
        assertEquals("их-токен", merge.state.theirPush)
        assertTrue(merge.state.theirs.first { it.seq == 3L }.deleted)

        val page = ChatPage(from = "nastya", index = 1, messages = (6..9).map { theirs(it) })
        val full = ChatLogic.mergeTheirsPage(merge.state, page)
        assertEquals((1L..12L).toList(), full.theirs.map { it.seq })

        val again = ChatLogic.mergeTheirs(full, head.copy(messages = (10..13).map { theirs(it) }, last = 13), 100)
        assertNull(again.missingFrom)
        assertEquals(13L, ChatLogic.lastSeq(again.state.theirs))

        // Сообщения с чужим «от кого» не принимаются.
        val stranger = ChatLogic.mergeTheirs(ChatState(), head.copy(messages = listOf(mine(1))), 1)
        assertTrue(stranger.state.theirs.isEmpty())
    }

    @Test
    fun deletedStaysDeletedAndClashingNumberKeepsTheOldMessage() {
        val state = ChatState(theirs = listOf(theirs(1), theirs(2).asDeleted()))
        val head = ChatHead(from = "nastya", last = 2, messages = listOf(theirs(1), theirs(2)))
        val merged = ChatLogic.mergeTheirs(state, head, 5).state
        assertTrue(merged.theirs[1].deleted)

        // Второй телефон начал заново: под тем же номером другое сообщение. Прежнее уходит в «раньше».
        val restarted = ChatHead(from = "nastya", last = 1, messages = listOf(theirs(1, at = 999_999, text = "заново")))
        val clash = ChatLogic.mergeTheirs(state, restarted, 6).state
        assertEquals("заново", clash.theirs.first { it.seq == 1L }.text)
        assertEquals("сообщение 1", clash.earlier.single().text)
    }

    @Test
    fun restartedNumberingLowersReadMarksSoNewMessagesAreNotReadAtOnce() {
        // Здесь прочитано всё до 5-го, а второй телефон начал заново и прислал только 1-е и 2-е.
        val state = ChatState(theirs = (1..5).map { theirs(it) }, readUpTo = 5, notifiedUpTo = 5, readSent = 5)
        val head = ChatHead(from = "nastya", last = 2, messages = listOf(theirs(1, at = 900_000, text = "новое 1"), theirs(2, at = 900_500, text = "новое 2")))
        val merge = ChatLogic.mergeTheirs(state, head, 7)
        assertTrue(merge.restarted)
        assertEquals(listOf("новое 1", "новое 2"), merge.state.theirs.map { it.text })
        assertEquals(5, merge.state.earlier.size)
        assertEquals(2L, merge.state.readUpTo)
        assertEquals(2L, merge.state.notifiedUpTo)
        assertEquals(-1L, merge.state.readSent)
        // Следующее его сообщение — непрочитанное, о нём будет уведомление, и у него не будет «прочитано».
        val next = ChatLogic.mergeTheirs(merge.state, head.copy(last = 3, messages = head.messages + theirs(3, at = 901_000)), 8)
        assertFalse(next.restarted)
        assertEquals(1, ChatLogic.unread(next.state))
        assertEquals(listOf(3L), ChatLogic.toNotify(next.state).map { it.seq })
        assertEquals(ChatTick.SENT, ChatLogic.tick(ChatState(mine = listOf(mine(3)), uploaded = 3, theirRead = 2), mine(3), true))
    }

    @Test
    fun unreadAndNotificationsCountOnlyNewMessagesOfTheOther() {
        val state = ChatState(
            mine = listOf(mine(1)),
            theirs = listOf(theirs(1), theirs(2), theirs(3).asDeleted(), theirs(4), theirs(5)),
            readUpTo = 1,
            notifiedUpTo = 4,
        )
        assertEquals(3, ChatLogic.unread(state))
        assertEquals(listOf(5L), ChatLogic.toNotify(state).map { it.seq })
        assertEquals(listOf(2L, 4L, 5L), ChatLogic.unreadMessages(state).map { it.seq })
    }

    @Test
    fun restoreTakesCloudHistoryAndRenumbersLocalDrafts() {
        val head = ChatHead(from = "aizat", pages = 1, last = 5, messages = listOf(mine(4), mine(5)), push = "старый")
        val page = ChatPage(from = "aizat", index = 0, messages = (1..3).map { mine(it) })
        val local = ChatLogic.append(ChatState(), aizat, "написал до сверки", 10_000)
        val restored = ChatLogic.restoreMine(local, head, listOf(page), ChatMark(version = 42, read = 7, typing = 0))
        assertEquals((1L..6L).toList(), restored.mine.map { it.seq })
        assertEquals("написал до сверки", restored.mine.last().text)
        assertEquals(5L, restored.uploaded)
        assertEquals(listOf(1L), restored.pageStarts)
        assertEquals(4L, restored.headStart)
        assertEquals(42L, restored.myVersion)
        assertEquals(7L, restored.readUpTo)
        assertEquals(7L, restored.notifiedUpTo)
        assertTrue(restored.restored)
        assertTrue(ChatLogic.hasPending(restored, "старый", null))

        val clean = ChatLogic.restoreMine(ChatState(), head, listOf(page), ChatMark(42, 0, 0))
        assertFalse(ChatLogic.hasPending(clean, "старый", null))
    }

    @Test
    fun newPairCodeStartsOverButKeepsTheHistoryAbove() {
        val state = ChatState(pair = "old", mine = listOf(mine(1)), theirs = listOf(theirs(1, at = 500)), restored = true, uploaded = 1)
        val renewed = ChatLogic.repair(state, "new")
        assertEquals("new", renewed.pair)
        assertTrue(renewed.mine.isEmpty())
        assertFalse(renewed.restored)
        assertEquals(listOf(500L, 1000L), renewed.earlier.map { it.at })
        assertEquals("tag", ChatLogic.repair(ChatState(), "tag").pair)
        assertEquals(state, ChatLogic.repair(state, "old"))
    }

    @Test
    fun rowsGroupMessagesSplitDaysAndShowTicks() {
        val evening = ZonedDateTime.of(2026, 11, 1, 21, 0, 0, 0, zone).toInstant().toEpochMilli()
        val state = ChatState(
            mine = listOf(mine(1, at = evening), mine(2, at = evening + 60_000), mine(3, at = evening + 86_400_000)),
            theirs = listOf(theirs(1, at = evening + 120_000, replyTo = "aizat:2")),
            uploaded = 2,
            theirRead = 1,
        )
        val rows = ChatLogic.rows(state, aizat, zone).asReversed()
        assertEquals(6, rows.size)
        assertTrue(rows[0] is ChatRow.Day)
        val first = rows[1] as ChatRow.Message
        assertTrue(first.first)
        assertFalse(first.last)
        assertEquals(ChatTick.READ, first.tick)
        val second = rows[2] as ChatRow.Message
        assertFalse(second.first)
        assertTrue(second.last)
        assertEquals(ChatTick.SENT, second.tick)
        val answer = rows[3] as ChatRow.Message
        assertFalse(answer.mine)
        assertEquals(ChatTick.NONE, answer.tick)
        assertEquals("aizat:2", answer.reply?.key)
        assertTrue(rows[4] is ChatRow.Day)
        assertEquals(ChatTick.PENDING, (rows[5] as ChatRow.Message).tick)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
        // Удалённое сообщение пропадает из ленты, а ответ на него — без цитаты.
        val withDeleted = state.copy(mine = state.mine.map { if (it.seq == 2L) it.asDeleted() else it })
        val visible = ChatLogic.rows(withDeleted, aizat, zone).filterIsInstance<ChatRow.Message>()
        assertEquals(3, visible.size)
        assertNull(visible.first { !it.mine }.reply)
        // На экране список идёт от новых к старым.
        assertTrue(ChatLogic.rows(state, aizat, zone).first() is ChatRow.Message)
    }

    @Test
    fun rowKeysStayUniqueWhenEarlierChatOverlapsDays() {
        val day1 = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val day2 = day1 + 86_400_000
        // «Раньше» заканчивается вторым днём, а нынешняя переписка начинается с первого — и то же сообщение там дважды.
        val old = theirs(7, at = day2)
        val state = ChatState(
            earlier = listOf(theirs(5, at = day1), old, old),
            mine = listOf(mine(1, at = day1 + 1000), mine(2, at = day2 + 1000)),
            theirs = listOf(theirs(1, at = day2 + 2000)),
        )
        val rows = ChatLogic.rows(state, aizat, zone)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
        assertEquals(1, rows.count { it is ChatRow.Message && it.earlier && it.message.seq == 7L })
    }

    @Test
    fun oldArchivePageDoesNotPushOutFreshMessage() {
        val fresh = theirs(1, at = 999_999, text = "заново")
        val stale = theirs(1, at = 1000, text = "давнее")
        var state = ChatState(theirs = listOf(fresh))
        // Голова и старая страница спорят о номере 1 при каждой сверке: свежее остаётся, давнее одно в «раньше».
        repeat(3) {
            state = ChatLogic.mergeTheirsPage(state, ChatPage(from = "nastya", index = 0, messages = listOf(stale)))
            state = ChatLogic.mergeTheirs(state, ChatHead(from = "nastya", last = 1, messages = listOf(fresh)), it.toLong()).state
        }
        assertEquals("заново", state.theirs.single().text)
        assertEquals(listOf("давнее"), state.earlier.map { it.text })
    }

    @Test
    fun previewFitsIntoAPushAndPhotosAreNamed() {
        val preview = ChatLogic.preview(ChatMessage("aizat", 3, 10, "щ".repeat(3000)))
        assertTrue(preview.text.toByteArray(Charsets.UTF_8).size <= 1503)
        assertTrue(preview.text.endsWith("…"))
        assertEquals(3L, preview.seq)
        val photo = ChatMessage("aizat", 4, 10, photo = ChatPhoto("x", 1, 1))
        assertEquals("Фото", ChatLogic.shortText(photo))
        assertEquals("Фото: море", ChatLogic.shortText(photo.copy(text = "море")))
        assertTrue(ChatLogic.preview(photo).photo)
        assertEquals("Сообщение удалено", ChatLogic.shortText(photo.asDeleted()))
    }

    @Test
    fun everythingSurvivesSavingAndLoading() {
        val state = ChatLogic.append(withMessages(2), aizat, "с фото", 5, replyTo = "nastya:1", photo = ChatPhoto("p", 3, 4))
            .copy(theirs = listOf(theirs(1)), pair = "tag", theirPush = "t")
        assertEquals(state, ChatLogic.decodeState(ChatLogic.encodeState(state)))
        assertNull(ChatLogic.decodeState("не json"))
        val head = ChatHead(from = "aizat", pages = 2, last = 3, messages = state.mine, hook = "h")
        assertEquals(head, ChatLogic.decodeHead(ChatLogic.encodeHead(head)))
        // В облако идут короткие имена полей, без пустых значений.
        val encoded = ChatLogic.encodeHead(head)
        assertFalse(encoded.contains("\"deleted\""))
        assertFalse(encoded.contains("\"d\":false"))
    }
}
