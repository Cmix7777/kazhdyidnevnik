package io.github.cmix7777.kazhdyidnevnik.ui

import android.icu.text.BreakIterator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberEffectTime
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Невидимые чернила: текст закрыт мерцающими блёстками, их стирают пальцем. Когда стёрта
 * больше чем половина, остальное тает само и сообщение остаётся открытым ([onOpened]).
 */
@Composable
internal fun InkCover(cover: Color, sparkle: Color, animate: Boolean, onOpened: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val cell = with(density) { 6.dp.toPx() }
    val brush = with(density) { 17.dp.toPx() }
    val grid = remember { arrayOfNulls<BooleanArray>(1) }
    var cols by remember { mutableIntStateOf(0) }
    var wiped by remember { mutableIntStateOf(0) }
    var opening by remember { mutableStateOf(false) }
    val fade = remember { Animatable(1f) }
    val time = rememberEffectTime(fps = 24, running = animate && !opening)
    LaunchedEffect(opening) {
        if (!opening) return@LaunchedEffect
        fade.animateTo(0f, tween(650))
        onOpened()
    }

    fun wipe(at: Offset) {
        val cells = grid[0] ?: return
        if (cols == 0 || opening) return
        val rows = cells.size / cols
        val r = ceil(brush / cell).toInt()
        val cx = (at.x / cell).toInt()
        val cy = (at.y / cell).toInt()
        var changed = 0
        for (y in (cy - r)..(cy + r)) {
            if (y !in 0 until rows) continue
            for (x in (cx - r)..(cx + r)) {
                if (x !in 0 until cols) continue
                val dx = (x + 0.5f) * cell - at.x
                val dy = (y + 0.5f) * cell - at.y
                if (dx * dx + dy * dy > brush * brush) continue
                val i = y * cols + x
                if (!cells[i]) {
                    cells[i] = true
                    changed++
                }
            }
        }
        if (changed > 0) {
            wiped += changed
            if (wiped > cells.size * 0.5f) opening = true
        }
    }

    Canvas(
        modifier = modifier
            .graphicsLayer { alpha = fade.value }
            .pointerInput(Unit) { detectTapGestures(onTap = { wipe(it) }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { wipe(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        wipe(change.position)
                    },
                )
            },
    ) {
        val c = ceil(size.width / cell).toInt().coerceAtLeast(1)
        val rows = ceil(size.height / cell).toInt().coerceAtLeast(1)
        if (grid[0]?.size != c * rows) {
            grid[0] = BooleanArray(c * rows)
            cols = c
            wiped = 0
        }
        val cells = grid[0] ?: return@Canvas
        wiped.let { }
        val t = time()
        val radius = cell * 0.74f
        for (y in 0 until rows) {
            for (x in 0 until c) {
                if (cells[y * c + x]) continue
                val center = Offset((x + 0.5f) * cell, (y + 0.5f) * cell)
                drawCircle(cover, radius, center)
            }
        }
        val dot = cell * 0.16f
        for (y in 0 until rows) {
            for (x in 0 until c) {
                val i = y * c + x
                if (cells[i]) continue
                for (k in 0..1) {
                    val h = (i * 7919 + k * 104729) % 9973
                    val ox = (h % 97) / 97f
                    val oy = (h % 89) / 89f
                    val phase = (h % 61) / 61f * 6.28f
                    val speed = 1.4f + (h % 13) / 13f * 2.2f
                    val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(phase + t * speed))
                    drawCircle(sparkle, dot * (0.7f + (h % 5) * 0.15f), Offset((x + ox) * cell, (y + oy) * cell), alpha = a)
                }
            }
        }
    }
}

/** Сообщение только из смайликов (1–3 штуки): рисуется крупно, без пузыря. 0 — обычный текст. */
internal object BigEmoji {
    fun count(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 40) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(trimmed)
        var start = it.first()
        var end = it.next()
        var count = 0
        while (end != BreakIterator.DONE) {
            val cluster = trimmed.substring(start, end)
            if (cluster.isNotBlank()) {
                if (!isEmoji(cluster)) return 0
                count++
                if (count > 3) return 0
            }
            start = end
            end = it.next()
        }
        return count
    }

    private fun isEmoji(cluster: String): Boolean {
        val cp = cluster.codePointAt(0)
        if (cluster.contains('⃣')) return true
        return cp >= 0x1F000 ||
            cp in 0x2600..0x27BF ||
            cp in 0x2300..0x23FF ||
            cp in 0x2B00..0x2BFF ||
            cp in 0x2190..0x21FF ||
            cp in 0x25AA..0x25FE ||
            cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 ||
            cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299 || cp == 0x24C2
    }

    /** Сердечки бьются, остальные просто подпрыгивают при появлении. */
    fun isHeart(text: String): Boolean = HEARTS.any { text.contains(it) }

    private val HEARTS = listOf("❤", "🧡", "💛", "💚", "💙", "💜", "🤍", "🖤", "🤎", "🩷", "💖", "💗", "💓", "💕", "💞")
}
