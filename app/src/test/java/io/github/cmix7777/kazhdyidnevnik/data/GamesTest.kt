package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Крестики-нолики: правила, ходы через переписку и то, что в ленте видна только последняя позиция. */
class GamesTest {

    private val aizat = Person.AIZAT
    private val nastya = Person.NASTYA

    private fun game(board: String, x: String = aizat.id) = ChatGame(id = "g1", board = board, x = x)

    @Test
    fun everyLineWins() {
        for (line in TicTacToe.LINES) {
            val board = CharArray(9) { '.' }
            line.forEach { board[it] = 'X' }
            // Ноликов на один меньше, чтобы доска была честной.
            val free = (0 until 9).filter { it !in line }.take(2)
            free.forEach { board[it] = 'O' }
            val text = String(board)
            assertArrayEquals(text, line, TicTacToe.winLine(text))
            assertEquals('X', TicTacToe.winner(text))
            assertTrue(TicTacToe.over(text))
        }
        assertNull(TicTacToe.winLine("XOXXOOOXX"))
        assertTrue("полная доска без линии — ничья", TicTacToe.over("XOXXOOOXX"))
        assertFalse(TicTacToe.over(TicTacToe.EMPTY))
    }

    @Test
    fun turnsGoInOrderAndOnlyToFreeCells() {
        val start = game(TicTacToe.EMPTY)
        assertTrue(TicTacToe.isTurnOf(start, aizat.id))
        assertFalse(TicTacToe.isTurnOf(start, nastya.id))
        assertNull("не её очередь", TicTacToe.move(start, 0, nastya.id))
        val one = TicTacToe.move(start, 4, aizat.id)!!
        assertEquals("....X....", one.board)
        assertEquals(4, one.last)
        assertNull("второй раз подряд нельзя", TicTacToe.move(one, 0, aizat.id))
        assertNull("занято", TicTacToe.move(one, 4, nastya.id))
        assertNull("нет такой клетки", TicTacToe.move(one, 9, nastya.id))
        val two = TicTacToe.move(one, 0, nastya.id)!!
        assertEquals("O...X....", two.board)
        assertEquals('O', TicTacToe.markOf(two, nastya.id))
    }

    @Test
    fun afterWinNobodyMoves() {
        val won = game("XXXOO....")
        assertEquals(aizat.id, TicTacToe.winnerId(won, aizat.id, nastya.id))
        assertEquals(aizat.id, TicTacToe.winnerId(won, nastya.id, aizat.id))
        assertNull(TicTacToe.move(won, 8, nastya.id))
        assertFalse(TicTacToe.isTurnOf(won, nastya.id))
    }

    @Test
    fun brokenBoardsAreNotPlayable() {
        assertFalse(TicTacToe.valid("XX......."))
        assertFalse(TicTacToe.valid("O........"))
        assertFalse(TicTacToe.valid("........"))
        assertFalse(TicTacToe.valid("........Z"))
        assertNull(TicTacToe.move(game("XX......."), 5, nastya.id))
    }

    @Test
    fun captionsAreWrittenFromTheMoverSide() {
        assertEquals("Крестики-нолики: давай сыграем", TicTacToe.caption(game(TicTacToe.EMPTY)))
        assertEquals("Крестики-нолики: твой ход", TicTacToe.caption(game("X........")))
        assertEquals("Крестики-нолики: у меня три в ряд", TicTacToe.caption(game("XXXOO....")))
        assertEquals("Крестики-нолики: ничья", TicTacToe.caption(game("XOXXOOOXX")))
    }

    /** Партия через «облако»: голова одного телефона переносится на другой, как в настоящей переписке. */
    private fun deliver(from: ChatState, sender: Person, to: ChatState, version: Long): ChatState {
        val head = ChatLogic.planUpload(from, sender, null, null).head
        return ChatLogic.mergeTheirs(to, head, version).state
    }

    @Test
    fun wholeGameThroughTheChatShowsOnlyTheLatestPosition() {
        var a = ChatState(restored = true)
        var n = ChatState(restored = true)
        a = ChatLogic.startGame(a, aizat, "g1", 1_000)
        val started = a.mine.single()
        assertEquals("Крестики-нолики: давай сыграем", started.text)
        assertEquals(aizat.id, started.game!!.x)
        assertFalse("ход нельзя править", ChatLogic.canEdit(a, started))

        // Айзат ходит в центр, Настя видит одну доску.
        a = ChatLogic.playGame(a, aizat, started.game!!, 4, 2_000)
        n = deliver(a, aizat, n, 1)
        val nRows = ChatLogic.rows(n, nastya).filterIsInstance<ChatRow.Message>()
        assertEquals(1, nRows.size)
        assertEquals("....X....", nRows.single().message.game!!.board)

        // Настя отвечает в угол, у Айзата снова одна доска — уже с её ходом.
        val seenByNastya = nRows.single().message.game!!
        n = ChatLogic.playGame(n, nastya, seenByNastya, 0, 3_000)
        a = deliver(n, nastya, a, 1)
        val aRows = ChatLogic.rows(a, aizat).filterIsInstance<ChatRow.Message>()
        assertEquals(1, aRows.size)
        assertEquals("O...X....", aRows.single().message.game!!.board)
        assertEquals(nastya.id, aRows.single().message.from)
        assertEquals("Крестики-нолики: твой ход", ChatLogic.shortText(aRows.single().message))
    }

    @Test
    fun doubleTapOrStalePositionDoesNotMoveTwice() {
        var a = ChatLogic.startGame(ChatState(restored = true), aizat, "g1", 1_000)
        val seen = a.mine.single().game!!
        a = ChatLogic.playGame(a, aizat, seen, 4, 2_000)
        val again = ChatLogic.playGame(a, aizat, seen, 0, 2_100)
        assertEquals("второе нажатие по старой позиции ничего не делает", a, again)
        val wrongTurn = ChatLogic.playGame(a, aizat, a.mine.last().game!!, 0, 2_200)
        assertEquals("ход Насти, не Айзата", a, wrongTurn)
        val unknown = ChatLogic.playGame(a, aizat, ChatGame("нет такой", x = aizat.id), 0, 2_300)
        assertEquals(a, unknown)
    }

    @Test
    fun newerPositionWinsEvenWhenClocksDisagree() {
        var a = ChatLogic.startGame(ChatState(restored = true), aizat, "g1", 10_000)
        a = ChatLogic.playGame(a, aizat, a.mine.single().game!!, 4, 11_000)
        // У Насти часы отстают: её ход «раньше» по времени, но в нём больше знаков.
        val reply = ChatMessage(from = nastya.id, seq = 1, at = 5_000, text = "x", game = a.mine.last().game!!.copy(board = "O...X....", last = 0))
        val state = a.copy(theirs = listOf(reply))
        assertEquals("O...X....", ChatLogic.latestGames(state)["g1"]!!.game!!.board)
        val rows = ChatLogic.rows(state, aizat).filterIsInstance<ChatRow.Message>()
        assertEquals(listOf(nastya.id), rows.map { it.message.from })
    }

    @Test
    fun deletingTheLastMoveShowsThePreviousOne() {
        var a = ChatLogic.startGame(ChatState(restored = true), aizat, "g1", 1_000)
        a = ChatLogic.playGame(a, aizat, a.mine.single().game!!, 4, 2_000)
        a = ChatLogic.deleteMine(a, a.mine.last().seq)
        assertNull(a.mine.last().game)
        val rows = ChatLogic.rows(a, aizat).filterIsInstance<ChatRow.Message>()
        assertEquals(TicTacToe.EMPTY, rows.single().message.game!!.board)
    }

    @Test
    fun twoGamesLiveSideBySide() {
        var a = ChatLogic.startGame(ChatState(restored = true), aizat, "g1", 1_000)
        a = ChatLogic.startGame(a, aizat, "g2", 2_000)
        a = ChatLogic.playGame(a, aizat, a.mine[0].game!!, 0, 3_000)
        val boards = ChatLogic.rows(a, aizat).filterIsInstance<ChatRow.Message>().map { it.message.game!!.id to it.message.game!!.board }.toSet()
        assertEquals(setOf("g1" to "X........", "g2" to TicTacToe.EMPTY), boards)
    }

    @Serializable
    private data class OldMessage(
        @SerialName("f") val from: String,
        @SerialName("s") val seq: Long,
        @SerialName("a") val at: Long,
        @SerialName("t") val text: String = "",
    )

    @Test
    fun oldAppVersionSeesTextAndNewOneSeesBoard() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        var a = ChatLogic.startGame(ChatState(restored = true), aizat, "g1", 1_000)
        a = ChatLogic.playGame(a, aizat, a.mine.single().game!!, 4, 2_000)
        val encoded = json.encodeToString(ChatMessage.serializer(), a.mine.last())
        val old = json.decodeFromString(OldMessage.serializer(), encoded)
        assertEquals("Крестики-нолики: твой ход", old.text)
        val back = json.decodeFromString(ChatMessage.serializer(), encoded)
        assertEquals("....X....", back.game!!.board)
        assertEquals(4, back.game!!.last)
        // Сообщение без партии (прежние версии) читается как раньше.
        assertNull(json.decodeFromString(ChatMessage.serializer(), """{"f":"nastya","s":1,"a":5,"t":"привет"}""").game)
    }

    @Test
    fun deletedGameMessageLosesTheBoard() {
        val a = ChatLogic.startGame(ChatState(restored = true), aizat, "g1", 1_000)
        assertNotNull(a.mine.single().game)
        assertNull(a.mine.single().asDeleted().game)
    }
}
