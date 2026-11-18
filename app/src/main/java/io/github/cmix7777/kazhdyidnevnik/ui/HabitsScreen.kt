package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.Habit
import io.github.cmix7777.kazhdyidnevnik.data.HabitStore
import io.github.cmix7777.kazhdyidnevnik.data.Habits
import io.github.cmix7777.kazhdyidnevnik.data.PartnerEventsRepository
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.pluralDays
import io.github.cmix7777.kazhdyidnevnik.notify.ReminderScheduler
import io.github.cmix7777.kazhdyidnevnik.service.Backups
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

private val Person.dative: String get() = if (this == Person.AIZAT) "Айзату" else "Насте"

/** Привычки: у владельца — свои, с галочками и настройками; на чужой странице — те, что второй открыл. */
@Composable
fun HabitsScreen(person: Person, isOwner: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mine by HabitStore.state.collectAsState()
    val partner by PartnerEventsRepository.state.collectAsState()
    LaunchedEffect(Unit) {
        HabitStore.init(context.filesDir)
        PartnerEventsRepository.init(context.filesDir)
    }
    val now by rememberNow()
    val today = now.toLocalDate()
    val habits = if (isOwner) mine else partner?.takeIf { it.from == person }?.habits.orEmpty()
    var input by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun change(edit: (List<Habit>) -> List<Habit>) {
        HabitStore.update(context.filesDir, edit)
        PartnerSync.syncSoon(context)
        ReminderScheduler.scheduleNext(context)
        scope.launch(Dispatchers.IO) { runCatching { Backups.saveAfterChange(context) } }
    }

    fun add() {
        val text = input.trim()
        if (text.isEmpty()) return
        change { Habits.add(it, UUID.randomUUID().toString(), text, System.currentTimeMillis()) }
        input = ""
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + LocalNavInset.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill(text = "Привычки", accent = true)
                TwoToneTitle(first = "Как идут", second = "привычки")
            }
        }
        if (isOwner) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.Chip)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it.take(Habits.MAX_TITLE) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.Violet),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp)
                            .testTag("habit-input"),
                        decorationBox = { inner ->
                            if (input.isEmpty()) Text("Новая привычка", style = MaterialTheme.typography.bodyLarge, color = Palette.TextFaint)
                            inner()
                        },
                    )
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = "Добавить привычку",
                        tint = Palette.Lavender,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { add() }
                            .padding(10.dp)
                            .testTag("habit-add"),
                    )
                }
            }
        }
        if (habits.isEmpty()) {
            item {
                InfoNote(
                    if (isOwner) "Добавь привычку и отмечай её каждый день: здесь будет видно, сколько дней подряд получается."
                    else "${person.shortName} пока не открыла привычки для ${person.partner.genitive}.",
                )
            }
        }
        items(habits, key = { it.id }) { habit ->
            HabitCard(
                habit = habit,
                today = today,
                isOwner = isOwner,
                partner = person.partner,
                expanded = open == habit.id,
                onToggleToday = { change { Habits.toggle(it, habit.id, today) } },
                onExpand = { open = if (open == habit.id) null else habit.id },
                onReminder = { hour -> change { Habits.setReminder(it, habit.id, hour) } },
                onShared = { shared -> change { Habits.setShared(it, habit.id, shared) } },
                onRemove = {
                    open = null
                    change { Habits.remove(it, habit.id) }
                },
            )
        }
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    today: java.time.LocalDate,
    isOwner: Boolean,
    partner: Person,
    expanded: Boolean,
    onToggleToday: () -> Unit,
    onExpand: () -> Unit,
    onReminder: (Int?) -> Unit,
    onShared: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val doneToday = Habits.isDone(habit, today)
    val streak = Habits.streak(habit, today)
    GlassCard(tone = if (doneToday) CardTone.Highlight else CardTone.Normal) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (isOwner) Modifier.clickable(onClick = onExpand) else Modifier),
            ) {
                Text(habit.title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(
                    text = if (streak > 0) "${pluralDays(streak.toLong())} подряд" else "Серии пока нет",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (doneToday) Palette.Violet else Color.Transparent)
                    .border(1.5.dp, if (doneToday) Palette.Violet else Palette.BorderStrong, CircleShape)
                    .then(if (isOwner) Modifier.clickable(onClick = onToggleToday) else Modifier)
                    .semantics {
                        contentDescription = "${habit.title}: отметка за сегодня"
                        stateDescription = if (doneToday) "сделано" else "не сделано"
                        role = Role.Checkbox
                    }
                    .testTag("habit-check"),
                contentAlignment = Alignment.Center,
            ) {
                if (doneToday) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Habits.history(habit, today).forEach { solved ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (solved) {
                                Modifier.background(Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep)))
                            } else {
                                Modifier
                                    .background(Palette.Chip)
                                    .border(1.dp, Palette.Border, RoundedCornerShape(6.dp))
                            },
                        ),
                )
            }
        }
        Text(
            text = when {
                !isOwner -> "последние 14 дней, сегодня справа"
                expanded -> "последние 14 дней"
                else -> listOfNotNull(
                    habit.remindHour?.let { "напомню в $it:00" },
                    if (habit.shared) "видно ${partner.dative}" else "только тебе",
                ).joinToString(" · ") + " · нажми на название, чтобы настроить"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        if (isOwner && expanded) {
            Text("Напоминание утром", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitChip("нет", habit.remindHour == null) { onReminder(null) }
                Habits.remindHours.forEach { hour ->
                    HabitChip("$hour:00", habit.remindHour == hour) { onReminder(hour) }
                }
            }
            Text("Кому видно", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitChip("${partner.dative} тоже", habit.shared) { onShared(true) }
                HabitChip("только мне", !habit.shared) { onShared(false) }
            }
            GhostButton("Удалить привычку", onClick = onRemove, small = true, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun HabitChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else Palette.TextMuted,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Palette.Violet else Palette.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
