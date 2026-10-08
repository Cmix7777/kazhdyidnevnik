package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.DayItem
import io.github.cmix7777.kazhdyidnevnik.formatTime
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Текущее время, обновляется раз в 30 секунд. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        delay(30_000)
        value = LocalDateTime.now()
    }
}

/** Карточка пары или смены. [now] — текущее время, если карточка за сегодня. */
@Composable
fun DayItemCard(item: DayItem, now: LocalTime?, modifier: Modifier = Modifier, dim: Boolean = false) {
    val active = now != null && now >= item.start && now < item.end
    val finished = now != null && now >= item.end
    val tone = when {
        active -> CardTone.Highlight
        dim || finished -> CardTone.Dim
        else -> CardTone.Normal
    }
    GlassCard(modifier = modifier, tone = tone, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(modifier = Modifier.width(50.dp)) {
                Text(
                    text = formatTime(item.start),
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                )
                Text(
                    text = formatTime(item.end),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) Palette.Lavender else Palette.TextMuted,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (active) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Palette.Lavender),
                        )
                        Text(text = "Сейчас", style = MaterialTheme.typography.labelMedium, color = Palette.Lavender)
                    }
                }
                when (item) {
                    is DayItem.LessonItem -> {
                        val lesson = item.lesson
                        Text(text = lesson.subject, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        val details = listOfNotNull(
                            lesson.number?.let { "$it пара" },
                            when {
                                lesson.online -> "онлайн"
                                lesson.room.isBlank() -> null
                                lesson.room.firstOrNull()?.isDigit() == true -> "ауд. ${lesson.room}"
                                else -> lesson.room
                            },
                        ).joinToString(" · ")
                        if (details.isNotEmpty()) {
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) Palette.Lavender else Palette.TextMuted,
                            )
                        }
                        if (lesson.teacher.isNotBlank()) {
                            Text(
                                text = lesson.teacher,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (active) Palette.TextMuted else Palette.TextFaint,
                            )
                        }
                        if (lesson.note.isNotBlank()) {
                            Text(
                                text = lesson.note.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Lavender,
                            )
                        }
                    }
                    is DayItem.WorkItem -> {
                        val hours = Duration.between(item.start, item.end).toMinutes() / 60.0
                        Text(text = "Работа", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            text = "смена " + if (hours % 1.0 == 0.0) "${hours.toInt()} ч" else "%.1f ч".format(hours),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

/** Неброская подсказка или предупреждение. */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    GlassCard(
        modifier = modifier,
        tone = if (isError) CardTone.Danger else CardTone.Normal,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isError) Palette.Danger else Palette.Violet),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else Palette.TextMuted,
            )
        }
    }
}

/**
 * Заголовок раздела: [text] белым, [accent] приглушённым рядом.
 * [badge] — таблетка справа (например, «сегодня»).
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, accent: String? = null, badge: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Palette.Text)) { append(text) }
                if (!accent.isNullOrEmpty()) {
                    withStyle(SpanStyle(color = Palette.TextMuted)) { append("  $accent") }
                }
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        if (badge != null) Pill(text = badge, accent = true)
    }
}
