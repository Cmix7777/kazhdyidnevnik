package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.ChatGame
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.TicTacToe
import io.github.cmix7777.kazhdyidnevnik.ui.fx.Haptics

/** Что написать под доской. */
internal fun gameStatus(game: ChatGame, me: Person): String {
    val partner = me.partner
    return when (TicTacToe.winnerId(game, me.id, partner.id)) {
        me.id -> "Три в ряд — победа твоя!"
        partner.id -> "Три в ряд у ${partner.genitive}"
        else -> when {
            TicTacToe.over(game.board) -> "Ничья"
            TicTacToe.isTurnOf(game, me.id) -> "Твой ход"
            else -> "Ход ${partner.genitive}"
        }
    }
}

/**
 * Крестики-нолики в пузыре: доска 3×3, чей ход и «Ещё партию» в конце.
 * [playable] — это последняя позиция партии и по ней можно ходить; [fresh] — ход только что пришёл, знак рисуется.
 */
@Composable
internal fun GameBubble(
    game: ChatGame,
    me: Person,
    textColor: Color,
    xColor: Color,
    oColor: Color,
    playable: Boolean,
    fresh: Boolean,
    onMove: (Int) -> Unit,
    onAgain: () -> Unit,
    meta: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val myTurn = playable && TicTacToe.isTurnOf(game, me.id)
    val over = TicTacToe.over(game.board)
    val line = TicTacToe.winLine(game.board)
    val appear = remember(game.board) { Animatable(if (fresh && game.last >= 0) 0f else 1f) }
    val strike = remember(game.board) { Animatable(if (fresh && line != null) 0f else 1f) }
    LaunchedEffect(game.board) {
        if (appear.value < 1f) appear.animateTo(1f, tween(380))
        if (strike.value < 1f) strike.animateTo(1f, tween(420))
    }
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 7.dp).testTag("game-${game.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Крестики-нолики",
            style = MaterialTheme.typography.labelMedium,
            color = textColor.copy(alpha = 0.75f),
        )
        Box(modifier = Modifier.size(174.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / 3f
                val grid = textColor.copy(alpha = 0.35f)
                val stroke = 2.dp.toPx()
                for (i in 1..2) {
                    drawLine(grid, Offset(cell * i, 6.dp.toPx()), Offset(cell * i, size.height - 6.dp.toPx()), stroke, StrokeCap.Round)
                    drawLine(grid, Offset(6.dp.toPx(), cell * i), Offset(size.width - 6.dp.toPx(), cell * i), stroke, StrokeCap.Round)
                }
                for (i in 0 until 9) {
                    val mark = game.board.getOrNull(i) ?: '.'
                    if (mark == '.') continue
                    val progress = if (i == game.last) appear.value else 1f
                    val center = Offset(cell * (i % 3) + cell / 2f, cell * (i / 3) + cell / 2f)
                    val dim = line != null && i !in line
                    val color = (if (mark == 'X') xColor else oColor).let { if (dim) it.copy(alpha = it.alpha * 0.45f) else it }
                    if (mark == 'X') drawCross(center, cell * 0.26f, color, 5.dp.toPx(), progress) else drawRing(center, cell * 0.27f, color, 5.dp.toPx(), progress)
                }
                if (line != null && strike.value > 0f) {
                    val a = Offset(cell * (line[0] % 3) + cell / 2f, cell * (line[0] / 3) + cell / 2f)
                    val c = Offset(cell * (line[2] % 3) + cell / 2f, cell * (line[2] / 3) + cell / 2f)
                    val dir = (c - a) / (c - a).getDistance().coerceAtLeast(1f)
                    val start = a - dir * (cell * 0.38f)
                    val end = c + dir * (cell * 0.38f)
                    drawLine(textColor.copy(alpha = 0.85f), start, start + (end - start) * strike.value, 4.dp.toPx(), StrokeCap.Round)
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0 until 3) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until 3) {
                            val i = r * 3 + c
                            val free = game.board.getOrNull(i) == '.'
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .semantics { contentDescription = "Клетка ${i + 1}" }
                                    .testTag("game-cell-$i")
                                    .clickable(enabled = myTurn && free) {
                                        Haptics.tick(context)
                                        onMove(i)
                                    },
                            )
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = gameStatus(game, me),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (myTurn || TicTacToe.winnerId(game, me.id, me.partner.id) == me.id) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                modifier = Modifier.testTag("game-status"),
            )
        }
        if (over && playable) {
            Text(
                text = "Ещё партию",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(textColor.copy(alpha = 0.16f))
                    .clickable { onAgain() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("game-again"),
            )
        }
        Box(modifier = Modifier.align(Alignment.End)) { meta() }
    }
}

private fun DrawScope.drawCross(center: Offset, r: Float, color: Color, width: Float, progress: Float) {
    val first = (progress * 2f).coerceIn(0f, 1f)
    val second = (progress * 2f - 1f).coerceIn(0f, 1f)
    val a = center + Offset(-r, -r)
    val b = center + Offset(r, r)
    if (first > 0f) drawLine(color, a, a + (b - a) * first, width, StrokeCap.Round)
    val c = center + Offset(r, -r)
    val d = center + Offset(-r, r)
    if (second > 0f) drawLine(color, c, c + (d - c) * second, width, StrokeCap.Round)
}

private fun DrawScope.drawRing(center: Offset, r: Float, color: Color, width: Float, progress: Float) {
    if (progress <= 0f) return
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = center - Offset(r, r),
        size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
}

/** Цвета крестиков и ноликов на своём и на чужом пузыре. */
internal fun gameColors(mineBubble: Boolean, accent: Color): Pair<Color, Color> =
    if (mineBubble) Color.White to Color(0xFFFFE08A) else accent to Color(0xFFE0679A)
