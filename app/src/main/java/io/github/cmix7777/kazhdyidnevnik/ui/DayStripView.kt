package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.DayStrip
import io.github.cmix7777.kazhdyidnevnik.formatTime
import io.github.cmix7777.kazhdyidnevnik.ui.fx.EffectsSettings
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberEffectTime
import java.time.LocalTime
import kotlin.math.sin

/**
 * Живая полоска дня: отрезки пар, работы и дел, прошедшее приглушено, текущий отрезок заполняется
 * до бусины «сейчас», от бусины расходится лёгкая волна. Под полоской одна строка: что идёт и что дальше.
 */
@Composable
fun DayStripView(strip: DayStrip, now: LocalTime, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val track = Palette.Text
    val labelColor = Palette.TextMuted
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val position by animateFloatAsState(strip.now, tween(900), label = "now")
    // Волна от бусины только пока день идёт; при «меньше анимаций» полоска неподвижна.
    val time = rememberEffectTime(fps = 20, running = EffectsSettings.animate(LocalContext.current))
    val label = formatTime(now)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        ) {
            val pad = 10.dp.toPx()
            val x0 = pad
            val x1 = size.width - pad
            val y = 26.dp.toPx()
            fun xAt(f: Float) = x0 + (x1 - x0) * f
            val nowX = xAt(position)
            val t = time()

            // Линия: прошедшая часть светлее.
            val lineH = 2.dp.toPx()
            drawRoundRect(track.copy(alpha = 0.12f), Offset(x0, y - lineH / 2), Size(x1 - x0, lineH), CornerRadius(lineH))
            drawRoundRect(track.copy(alpha = 0.26f), Offset(x0, y - lineH / 2), Size(nowX - x0, lineH), CornerRadius(lineH))

            val blockH = 8.dp.toPx()
            val radius = CornerRadius(blockH / 2)
            strip.blocks.forEach { block ->
                val color = when (block.kind) {
                    DayStrip.Kind.LESSON -> accent.primary
                    DayStrip.Kind.WORK -> accent.light
                    DayStrip.Kind.EVENT -> accent.glow
                }
                val left = xAt(block.start)
                val width = (xAt(block.end) - left).coerceAtLeast(blockH)
                val top = y - blockH / 2
                when {
                    position >= block.end -> drawRoundRect(color.copy(alpha = 0.28f), Offset(left, top), Size(width, blockH), radius)
                    position < block.start -> drawRoundRect(color.copy(alpha = 0.6f), Offset(left, top), Size(width, blockH), radius)
                    else -> {
                        // Текущий отрезок: мягкое свечение вокруг и заливка до «сейчас».
                        val pulse = 0.18f + 0.1f * (0.5f + 0.5f * sin(t * 3f))
                        for (i in 1..3) {
                            val grow = i * 2.dp.toPx()
                            drawRoundRect(
                                color.copy(alpha = pulse / i),
                                Offset(left - grow, top - grow),
                                Size(width + grow * 2, blockH + grow * 2),
                                CornerRadius(blockH / 2 + grow),
                            )
                        }
                        drawRoundRect(color.copy(alpha = 0.32f), Offset(left, top), Size(width, blockH), radius)
                        clipRect(left = left, top = top - 1f, right = nowX, bottom = top + blockH + 1f) {
                            drawRoundRect(color, Offset(left, top), Size(width, blockH), radius)
                        }
                    }
                }
            }

            drawNowBead(nowX, y, accent.primary, t)

            // Время над бусиной.
            val text = measurer.measure(label, labelStyle.copy(color = labelColor))
            val textX = (nowX - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(textX, 0f))
        }
        Text(
            text = strip.info,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

/** Бусина «сейчас»: светящееся ядро, след по линии назад и волна, которая расходится и тает. */
private fun DrawScope.drawNowBead(x: Float, y: Float, color: Color, t: Float) {
    val trail = 40.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.55f)), startX = x - trail, endX = x),
        topLeft = Offset(x - trail, y - 1.5.dp.toPx()),
        size = Size(trail, 3.dp.toPx()),
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = 0.75f), Color.Transparent), center = Offset(x, y), radius = 11.dp.toPx()),
        radius = 11.dp.toPx(),
        center = Offset(x, y),
    )
    val phase = (t * 0.6f) % 1f
    if (t > 0f) {
        drawCircle(
            color = color.copy(alpha = (1f - phase) * 0.6f),
            radius = 4.dp.toPx() + phase * 12.dp.toPx(),
            center = Offset(x, y),
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }
    drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(x, y))
}
