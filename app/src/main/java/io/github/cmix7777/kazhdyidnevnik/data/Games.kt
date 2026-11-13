package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Партия в крестики-нолики. Каждый ход — новое сообщение с той же партией [id]: так ход доходит
 * до второго телефона обычной перепиской, а в ленте видна только последняя позиция.
 */
@Serializable
data class ChatGame(
    @SerialName("i") val id: String,
    /** Девять клеток построчно: '.', 'X' или 'O'. */
    @SerialName("b") val board: String = TicTacToe.EMPTY,
    /** Кто играет крестиками; крестики ходят первыми. */
    @SerialName("x") val x: String,
    /** Клетка последнего хода (-1 — ходов ещё не было). */
    @SerialName("l") val last: Int = -1,
)

/** Правила крестиков-ноликов 3×3. */
object TicTacToe {
    const val EMPTY = "........."

    val LINES: List<IntArray> = listOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
    )

    /** Доска честная: девять клеток, крестиков столько же или на один больше, чем ноликов. */
    fun valid(board: String): Boolean {
        if (board.length != 9 || board.any { it != '.' && it != 'X' && it != 'O' }) return false
        val x = board.count { it == 'X' }
        val o = board.count { it == 'O' }
        return x - o in 0..1
    }

    fun marks(board: String): Int = board.count { it == 'X' || it == 'O' }

    /** Три в ряд: клетки линии или null. */
    fun winLine(board: String): IntArray? =
        if (board.length != 9) null else LINES.firstOrNull { (a, b, c) -> board[a] != '.' && board[a] == board[b] && board[b] == board[c] }

    fun winner(board: String): Char? = winLine(board)?.let { board[it[0]] }

    fun over(board: String): Boolean = winLine(board) != null || '.' !in board

    /** Чей знак ставится следующим. */
    fun nextMark(board: String): Char = if (board.count { it == 'X' } == board.count { it == 'O' }) 'X' else 'O'

    /** Знак человека в партии. */
    fun markOf(game: ChatGame, who: String): Char = if (game.x == who) 'X' else 'O'

    fun isTurnOf(game: ChatGame, who: String): Boolean =
        valid(game.board) && !over(game.board) && nextMark(game.board) == markOf(game, who)

    /** Победитель: id того, кто собрал три в ряд, или null (ничья или партия идёт). [other] — второй игрок. */
    fun winnerId(game: ChatGame, me: String, other: String): String? = when (winner(game.board)) {
        null -> null
        markOf(game, me) -> me
        else -> other
    }

    /** Ход [who] в клетку [cell]; null, если так ходить нельзя (не его очередь, клетка занята, партия кончилась). */
    fun move(game: ChatGame, cell: Int, who: String): ChatGame? {
        if (cell !in 0..8 || !isTurnOf(game, who) || game.board[cell] != '.') return null
        val board = StringBuilder(game.board).also { it.setCharAt(cell, markOf(game, who)) }.toString()
        return game.copy(board = board, last = cell)
    }

    /**
     * Текст сообщения с ходом: его видит второй телефон в уведомлении (и прежние версии приложения вместо доски).
     * Пишется от лица того, кто ходил.
     */
    fun caption(game: ChatGame): String = when {
        marks(game.board) == 0 -> "Крестики-нолики: давай сыграем"
        winner(game.board) != null -> "Крестики-нолики: у меня три в ряд"
        over(game.board) -> "Крестики-нолики: ничья"
        else -> "Крестики-нолики: твой ход"
    }
}
