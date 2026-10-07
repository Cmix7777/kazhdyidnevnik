package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
fun DayItemCard(item: DayItem, now: LocalTime?, modifier: Modifier = Modifier) {
    val active = now != null && now >= item.start && now < item.end
    val colors = MaterialTheme.colorScheme
    val container = when {
        active -> colors.primaryContainer
        item is DayItem.WorkItem -> colors.secondaryContainer
        else -> colors.surfaceContainerLow
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.width(48.dp)) {
                Text(
                    text = formatTime(item.start),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatTime(item.end),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (active) {
                    Text(
                        text = "Сейчас",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                }
                when (item) {
                    is DayItem.LessonItem -> {
                        val lesson = item.lesson
                        Text(text = lesson.subject, style = MaterialTheme.typography.titleMedium)
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
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (lesson.teacher.isNotBlank()) {
                            Text(
                                text = lesson.teacher,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    is DayItem.WorkItem -> {
                        val hours = Duration.between(item.start, item.end).toMinutes() / 60.0
                        Text(text = "Работа", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "смена " + if (hours % 1.0 == 0.0) "${hours.toInt()} ч" else "%.1f ч".format(hours),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
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
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) colors.errorContainer else colors.tertiaryContainer,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) colors.onErrorContainer else colors.onTertiaryContainer,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
