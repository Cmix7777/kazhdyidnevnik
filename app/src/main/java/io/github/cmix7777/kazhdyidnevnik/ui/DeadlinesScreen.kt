package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.Deadline
import io.github.cmix7777.kazhdyidnevnik.data.Occasion
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.countdownText
import io.github.cmix7777.kazhdyidnevnik.data.daysBetween
import io.github.cmix7777.kazhdyidnevnik.formatDate
import java.time.LocalDate

@Composable
fun DeadlinesScreen(diary: DiaryViewModel, person: Person, isOwner: Boolean, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val ownIds = diary.diary.deadlines.mapNotNull { it.id }.toSet()
    // Особые даты общие: видны на обеих страницах. Обычные важные даты — только на телефоне владельца.
    val special = diary.specialDates().sortedBy { it.nextDate(today).toEpochDay() }
    val deadlines = person.profile.deadlines + if (isOwner) diary.diary.deadlines.filter { !it.special } else emptyList()
    val (upcoming, past) = deadlines.sortedBy { it.date.toEpochDay() }.partition { !it.date.isBefore(today) }
    var editing by remember { mutableStateOf<Deadline?>(null) }
    var adding by remember { mutableStateOf(false) }
    val open: (Deadline) -> (() -> Unit)? = { deadline ->
        if (deadline.id != null && deadline.id in ownIds) ({ editing = deadline }) else null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill(text = "Дедлайны", accent = true)
                TwoToneTitle(first = "Что впереди", second = "важные даты")
                if (isOwner) {
                    GhostButton(text = "+ Добавить дату", onClick = { adding = true }, small = true)
                }
            }
        }
        if (special.isNotEmpty()) {
            item { SectionTitle("Особые дни") }
            itemsIndexed(special, key = { _, d -> "special-${d.id}" }) { _, deadline ->
                SpecialCard(
                    deadline = deadline,
                    today = today,
                    addedBy = if (deadline.id in ownIds) null else diary.partnerShare?.from,
                    onClick = open(deadline),
                )
            }
            if (deadlines.isNotEmpty()) item { SectionTitle("Остальные даты") }
        }
        if (deadlines.isEmpty() && special.isEmpty()) {
            item {
                InfoNote(
                    if (isOwner) {
                        "Здесь будут важные даты: зачёты, экзамены, дни рождения, годовщины. " +
                            "Добавь первую кнопкой выше, приложение будет считать дни до неё."
                    } else {
                        "Важные даты ${person.genitive} хранятся на её телефоне. Дни рождения и годовщины, " +
                            "которые она добавит, появятся здесь сами."
                    },
                )
            }
        }
        itemsIndexed(upcoming) { index, deadline ->
            DeadlineCard(
                deadline,
                today,
                tone = if (index == 0) CardTone.Highlight else CardTone.Normal,
                onClick = open(deadline),
            )
        }
        if (past.isNotEmpty()) {
            item { SectionTitle("Уже прошло") }
            itemsIndexed(past) { _, deadline -> DeadlineCard(deadline, today, tone = CardTone.Dim, onClick = open(deadline)) }
        }
        if (person.profile.deadlines.isNotEmpty()) item {
            Text(
                text = "Примерные даты помечены знаком «≈». Когда узнаешь точную дату, напиши Claude, и он поправит.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }

    if (adding) {
        DeadlineEditor(
            initial = null,
            onDismiss = { adding = false },
            onSave = {
                diary.saveDeadline(it)
                adding = false
            },
            onDelete = { adding = false },
        )
    }
    editing?.let { deadline ->
        DeadlineEditor(
            initial = deadline,
            onDismiss = { editing = null },
            onSave = {
                diary.saveDeadline(it)
                editing = null
            },
            onDelete = {
                deadline.id?.let { id -> diary.deleteDeadline(id) }
                editing = null
            },
        )
    }
}

/** Особая дата: повод, сколько дней до ближайшего раза, сколько лет исполнится или сколько вместе. */
@Composable
private fun SpecialCard(deadline: Deadline, today: LocalDate, addedBy: Person?, onClick: (() -> Unit)?) {
    val next = deadline.nextDate(today)
    val days = daysBetween(today, next)
    val modifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    } else {
        Modifier
    }
    GlassCard(modifier = modifier, tone = if (days == 0L) CardTone.Highlight else CardTone.Normal) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        when (deadline.occasion) {
                            Occasion.ANNIVERSARY -> TogetherColors.aizatTop
                            else -> deadline.whose?.let { TogetherColors.top(it) } ?: Palette.Violet
                        },
                    ),
            )
            Text(
                text = deadline.shownTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
            )
            Pill(text = (deadline.occasion?.title ?: "").lowercase(), accent = days == 0L)
        }
        Text(
            text = if (days == 0L) "сегодня" else countdownText(days),
            style = MaterialTheme.typography.headlineMedium,
            color = if (days == 0L) Palette.Text else Palette.Lavender,
        )
        Text(
            text = listOfNotNull(formatDate(next), deadline.yearsText(next)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
        )
        if (addedBy != null) {
            Text(
                text = if (addedBy == Person.NASTYA) "добавила Настя" else "добавил Айзат",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        } else {
            deadline.note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint) }
        }
    }
}

@Composable
private fun DeadlineCard(deadline: Deadline, today: LocalDate, tone: CardTone, onClick: (() -> Unit)? = null) {
    val days = daysBetween(today, deadline.date)
    val past = tone == CardTone.Dim
    val modifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    } else {
        Modifier
    }
    GlassCard(modifier = modifier, tone = tone) {
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
