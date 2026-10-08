package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.Deadline
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.countdownText
import io.github.cmix7777.kazhdyidnevnik.data.daysBetween
import io.github.cmix7777.kazhdyidnevnik.formatDate
import java.time.LocalDate

@Composable
fun DeadlinesScreen(person: Person, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val deadlines = person.profile.deadlines
    val (upcoming, past) = deadlines.sortedBy { it.date.toEpochDay() }.partition { !it.date.isBefore(today) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill(text = "Дедлайны", accent = true)
                TwoToneTitle(first = "Что впереди", second = "важные даты")
            }
        }
        if (deadlines.isEmpty()) {
            item {
                InfoNote(
                    "Здесь будут важные даты ${person.genitive}: зачёты, экзамены, сдача работ и практика. " +
                        "Пока их нет — скоро их можно будет добавлять и менять прямо в приложении.",
                )
            }
        }
        itemsIndexed(upcoming) { index, deadline ->
            DeadlineCard(deadline, today, tone = if (index == 0) CardTone.Highlight else CardTone.Normal)
        }
        if (past.isNotEmpty()) {
            item { SectionTitle("Уже прошло") }
            itemsIndexed(past) { _, deadline -> DeadlineCard(deadline, today, tone = CardTone.Dim) }
        }
        if (deadlines.isNotEmpty()) item {
            Text(
                text = "Примерные даты помечены знаком «≈». Когда узнаешь точную дату, напиши Claude, и он поправит.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun DeadlineCard(deadline: Deadline, today: LocalDate, tone: CardTone) {
    val days = daysBetween(today, deadline.date)
    val past = tone == CardTone.Dim
    GlassCard(tone = tone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = deadline.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
            )
            if (tone == CardTone.Highlight) Pill(text = "ближайший", accent = true)
        }
        Text(
            text = (if (deadline.approximate != null) "≈ " else "") + countdownText(days),
            style = MaterialTheme.typography.headlineMedium,
            color = when {
                past -> Palette.TextMuted
                tone == CardTone.Highlight -> Palette.Text
                else -> Palette.Lavender
            },
        )
        Text(
            text = deadline.approximate ?: formatDate(deadline.date),
            style = MaterialTheme.typography.bodyMedium,
            color = if (tone == CardTone.Highlight) Palette.Lavender else Palette.TextMuted,
        )
        deadline.note?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
    }
}
