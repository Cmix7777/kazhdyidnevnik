package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.Deadline
import io.github.cmix7777.kazhdyidnevnik.data.Deadlines
import io.github.cmix7777.kazhdyidnevnik.data.countdownText
import io.github.cmix7777.kazhdyidnevnik.data.daysBetween
import io.github.cmix7777.kazhdyidnevnik.formatDate
import java.time.LocalDate

@Composable
fun DeadlinesScreen(modifier: Modifier = Modifier) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val (upcoming, past) = Deadlines.default.sortedBy { it.date.toEpochDay() }.partition { !it.date.isBefore(today) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Дедлайны", style = MaterialTheme.typography.headlineSmall) }
        items(upcoming) { DeadlineCard(it, today, isPast = false) }
        if (past.isNotEmpty()) {
            item { SectionTitle("Уже прошло") }
            items(past) { DeadlineCard(it, today, isPast = true) }
        }
        item {
            Text(
                text = "Примерные даты помечены словом «примерно». Когда узнаешь точную дату, напиши Claude, и он поправит.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeadlineCard(deadline: Deadline, today: LocalDate, isPast: Boolean) {
    val colors = MaterialTheme.colorScheme
    val days = daysBetween(today, deadline.date)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) colors.surfaceContainerLowest else colors.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = deadline.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = (if (deadline.approximate != null) "≈ " else "") + countdownText(days),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isPast) colors.onSurfaceVariant else colors.primary,
            )
            Text(
                text = deadline.approximate ?: formatDate(deadline.date),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            deadline.note?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
