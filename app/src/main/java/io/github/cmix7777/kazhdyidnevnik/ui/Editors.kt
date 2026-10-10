package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.cmix7777.kazhdyidnevnik.data.Deadline
import io.github.cmix7777.kazhdyidnevnik.data.EventKind
import io.github.cmix7777.kazhdyidnevnik.data.MoneyCategories
import io.github.cmix7777.kazhdyidnevnik.data.MoneyEntry
import io.github.cmix7777.kazhdyidnevnik.data.MoneyFormat
import io.github.cmix7777.kazhdyidnevnik.data.UserEvent
import io.github.cmix7777.kazhdyidnevnik.formatDate
import io.github.cmix7777.kazhdyidnevnik.formatShortDay
import io.github.cmix7777.kazhdyidnevnik.formatTime
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Окно-редактор в стиле карточек приложения. */
@Composable
fun EditorDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Palette.CardTop, Palette.CardBottom)))
                    .border(1.dp, Palette.BorderStrong, shape)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                content()
            }
        }
    }
}

/** Таблетка-вариант: выбранная залита цветом палитры. */
@Composable
fun SelectPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (selected) Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep)) else SolidColor(Palette.Chip),
            )
            .border(1.dp, if (selected) Color.Transparent else Palette.BorderStrong, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else Palette.TextMuted,
        )
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            keyboardType = keyboardType,
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Violet,
            unfocusedBorderColor = Palette.BorderStrong,
            focusedLabelColor = Palette.Lavender,
            unfocusedLabelColor = Palette.TextMuted,
            cursorColor = Palette.Lavender,
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
        ),
    )
}

@Composable
private fun EditorLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
}

@Composable
private fun EditorSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Palette.Violet,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Palette.TextMuted,
                uncheckedTrackColor = Palette.Chip,
                uncheckedBorderColor = Palette.BorderStrong,
            ),
        )
    }
}

/** Кнопки внизу редактора: сохранить, отмена и, если есть, удаление. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorButtons(canSave: Boolean, onSave: () -> Unit, onDismiss: () -> Unit, extra: @Composable () -> Unit = {}) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillButton(text = "Сохранить", onClick = onSave, enabled = canSave)
        GhostButton(text = "Отмена", onClick = onDismiss)
        extra()
    }
}

private enum class Pick { Date, Start, End }

private fun everyWeekday(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "каждый понедельник"
    DayOfWeek.TUESDAY -> "каждый вторник"
    DayOfWeek.WEDNESDAY -> "каждую среду"
    DayOfWeek.THURSDAY -> "каждый четверг"
    DayOfWeek.FRIDAY -> "каждую пятницу"
    DayOfWeek.SATURDAY -> "каждую субботу"
    DayOfWeek.SUNDAY -> "каждое воскресенье"
}

/**
 * Добавить или изменить своё дело. [date] — день, на котором его открыли
 * (для нового дела — день по умолчанию, для повторяющегося — день, который можно убрать).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditor(
    initial: UserEvent?,
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (UserEvent) -> Unit,
    onDelete: () -> Unit,
    onSkipDay: () -> Unit,
) {
    val defaultStart = LocalTime.of((LocalTime.now().hour + 1).coerceIn(8, 21), 0)
    var kind by remember { mutableStateOf(initial?.kind ?: EventKind.WORK) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var day by remember { mutableStateOf(initial?.date ?: date) }
    var start by remember { mutableStateOf(initial?.start ?: defaultStart) }
    var end by remember { mutableStateOf(initial?.end ?: defaultStart.plusHours(1)) }
    var weekly by remember { mutableStateOf(initial?.weekly ?: false) }
    var remind by remember { mutableStateOf(initial?.remindBefore) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var picking by remember { mutableStateOf<Pick?>(null) }
    val valid = end.isAfter(start)

    EditorDialog(title = if (initial == null) "Новое дело" else "Дело", onDismiss = onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EventKind.entries.forEach { option ->
                SelectPill(text = option.title, selected = kind == option, onClick = { kind = option })
            }
        }
        EditorField(value = title, onValueChange = { title = it }, label = "Название (можно не писать)")

        EditorLabel(if (weekly) "С какого дня" else "Когда")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(text = formatShortDay(day), onClick = { picking = Pick.Date }, small = true)
            GhostButton(text = "с ${formatTime(start)}", onClick = { picking = Pick.Start }, small = true)
            GhostButton(text = "до ${formatTime(end)}", onClick = { picking = Pick.End }, small = true)
        }
        if (!valid) {
            Text(text = "Конец должен быть позже начала.", style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
        }

        EditorSwitch(
            title = "Повторять каждую неделю",
            subtitle = everyWeekday(day),
            checked = weekly,
            onChange = { weekly = it },
        )

        EditorLabel("Напомнить")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UserEvent.reminderChoices.forEach { minutes ->
                SelectPill(text = UserEvent.reminderText(minutes), selected = remind == minutes, onClick = { remind = minutes })
            }
        }

        EditorField(value = note, onValueChange = { note = it }, label = "Заметка", singleLine = false)

        EditorButtons(
            canSave = valid,
            onSave = {
                onSave(
                    UserEvent(
                        id = initial?.id ?: DiaryViewModel.newId(),
                        kind = kind,
                        title = title.trim(),
                        date = day,
                        start = start,
                        end = end,
                        weekly = weekly,
                        skipped = if (weekly) initial?.skipped.orEmpty() else emptySet(),
                        remindBefore = remind,
                        note = note.trim(),
                    ),
                )
            },
            onDismiss = onDismiss,
        ) {
            if (initial != null) {
                if (initial.weekly && initial.occursOn(date)) {
                    GhostButton(text = "Убрать только ${formatShortDay(date)}", onClick = onSkipDay)
                    GhostButton(text = "Удалить все повторы", onClick = onDelete)
                } else {
                    GhostButton(text = "Удалить", onClick = onDelete)
                }
            }
        }
    }

    when (picking) {
        Pick.Date -> DateDialog(initial = day, onDismiss = { picking = null }, onConfirm = {
            day = it
            picking = null
        })
        Pick.Start -> TimeDialog(title = "Начало", initial = start, onDismiss = { picking = null }, onConfirm = { time ->
            // Длительность сохраняется: сдвигаем и конец.
            val minutes = Duration.between(start, end).toMinutes().coerceAtLeast(30)
            val newEnd = time.plusMinutes(minutes)
            start = time
            end = if (newEnd.isAfter(time)) newEnd else LocalTime.of(23, 59)
            picking = null
        })
        Pick.End -> TimeDialog(title = "Конец", initial = end, onDismiss = { picking = null }, onConfirm = {
            end = it
            picking = null
        })
        null -> Unit
    }
}

/** Что открыто в редакторе дела: само дело (null — новое) и день, на котором нажали. */
data class EventEdit(val event: UserEvent?, val date: LocalDate)

/** Редактор дела, подключённый к дневнику. */
@Composable
fun EventEditorHost(edit: EventEdit, diary: DiaryViewModel, onClose: () -> Unit) {
    EventEditor(
        initial = edit.event,
        date = edit.date,
        onDismiss = onClose,
        onSave = {
            diary.saveEvent(it)
            onClose()
        },
        onDelete = {
            edit.event?.let { event -> diary.deleteEvent(event.id) }
            onClose()
        },
        onSkipDay = {
            edit.event?.let { event -> diary.skipDay(event, edit.date) }
            onClose()
        },
    )
}

/** Добавить или изменить важную дату. */
@Composable
fun DeadlineEditor(
    initial: Deadline?,
    onDismiss: () -> Unit,
    onSave: (Deadline) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().plusDays(7)) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var picking by remember { mutableStateOf(false) }

    EditorDialog(title = if (initial == null) "Новая важная дата" else "Важная дата", onDismiss = onDismiss) {
        EditorField(value = title, onValueChange = { title = it }, label = "Что (зачёт, экзамен, сдача работы…)")
        EditorLabel("Когда")
        GhostButton(text = formatDate(date), onClick = { picking = true }, small = true)
        EditorField(value = note, onValueChange = { note = it }, label = "Заметка", singleLine = false)
        EditorButtons(
            canSave = title.isNotBlank(),
            onSave = {
                onSave(
                    Deadline(
                        title = title.trim(),
                        date = date,
                        note = note.trim().ifEmpty { null },
                        id = initial?.id ?: DiaryViewModel.newId(),
                    ),
                )
            },
            onDismiss = onDismiss,
        ) {
            if (initial != null) GhostButton(text = "Удалить", onClick = onDelete)
        }
    }
    if (picking) {
        DateDialog(initial = date, onDismiss = { picking = false }, onConfirm = {
            date = it
            picking = false
        })
    }
}

/** Добавить или изменить запись бюджета: «плюс» — доход, «минус» — трата. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyEditor(
    initial: MoneyEntry?,
    income: Boolean,
    onDismiss: () -> Unit,
    onSave: (MoneyEntry) -> Unit,
    onDelete: () -> Unit,
) {
    var isIncome by remember { mutableStateOf(initial?.income ?: income) }
    var amount by remember { mutableStateOf(initial?.amount?.let { MoneyFormat.forInput(it) }.orEmpty()) }
    var category by remember {
        mutableStateOf(initial?.category ?: if (isIncome) MoneyCategories.income.first() else MoneyCategories.expense.first())
    }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    val parsed = MoneyFormat.parse(amount)
    val categories = if (isIncome) MoneyCategories.income else MoneyCategories.expense

    EditorDialog(
        title = when {
            initial != null -> "Запись бюджета"
            isIncome -> "Плюс: доход"
            else -> "Минус: трата"
        },
        onDismiss = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectPill(text = "− Трата", selected = !isIncome, onClick = {
                isIncome = false
                if (category !in MoneyCategories.expense) category = MoneyCategories.expense.first()
            })
            SelectPill(text = "+ Доход", selected = isIncome, onClick = {
                isIncome = true
                if (category !in MoneyCategories.income) category = MoneyCategories.income.first()
            })
        }
        EditorField(
            value = amount,
            onValueChange = { amount = it },
            label = "Сумма, ₽",
            keyboardType = KeyboardType.Decimal,
            error = if (amount.isNotBlank() && parsed == null) "Нужна сумма больше нуля, например 1500 или 99,90" else null,
        )
        EditorLabel(if (isIncome) "Откуда" else "Куда")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { option ->
                SelectPill(text = option, selected = category == option, onClick = { category = option })
            }
        }
        EditorField(
            value = note,
            onValueChange = { note = it },
            label = if (isIncome) "Подпись (можно не писать)" else "На что потрачено (можно не писать)",
        )
        EditorLabel("Дата")
        GhostButton(text = formatDate(date), onClick = { picking = true }, small = true)
        EditorButtons(
            canSave = parsed != null,
            onSave = {
                parsed?.let { value ->
                    onSave(
                        MoneyEntry(
                            id = initial?.id ?: DiaryViewModel.newId(),
                            date = date,
                            amount = value,
                            income = isIncome,
                            category = category,
                            note = note.trim(),
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                }
            },
            onDismiss = onDismiss,
        ) {
            if (initial != null) GhostButton(text = "Удалить", onClick = onDelete)
        }
    }
    if (picking) {
        DateDialog(initial = date, onDismiss = { picking = false }, onConfirm = {
            date = it
            picking = false
        })
    }
}
