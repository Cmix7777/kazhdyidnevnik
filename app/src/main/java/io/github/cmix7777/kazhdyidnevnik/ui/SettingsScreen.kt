package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cmix7777.kazhdyidnevnik.BuildConfig
import io.github.cmix7777.kazhdyidnevnik.formatTime
import io.github.cmix7777.kazhdyidnevnik.notify.Notifier
import io.github.cmix7777.kazhdyidnevnik.notify.SystemSettings
import java.time.LocalTime

private enum class TimeField { Morning, Evening }

@Composable
fun SettingsScreen(vm: ScheduleViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeCount++
        onPauseOrDispose { }
    }
    val notificationsOn = remember(resumeCount) { Notifier.canPost(context) }
    val noBatteryLimits = remember(resumeCount) { SystemSettings.isIgnoringBatteryOptimizations(context) }
    val exactAlarms = remember(resumeCount) { SystemSettings.canScheduleExact(context) }
    val status = remember(resumeCount, vm.statusVersion) { vm.statusText() }
    val settings = vm.reminderSettings
    var editing by remember { mutableStateOf<TimeField?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Назад") }
                Text("Настройки", style = MaterialTheme.typography.headlineSmall)
            }
        }

        if (!notificationsOn) {
            item {
                ActionCard(
                    text = "Уведомления для приложения выключены, напоминания не придут.",
                    button = "Включить уведомления",
                    isError = true,
                    onClick = { SystemSettings.openNotificationSettings(context) },
                )
            }
        }

        item { SectionTitle("Уведомления") }
        item {
            SwitchRow(
                title = "Изменения в расписании",
                subtitle = "Сайт проверяется в 23:00, в 6:30 и примерно раз в 3 часа",
                checked = settings.changes,
                onChange = { on -> vm.updateReminders { it.copy(changes = on) } },
            )
        }
        item {
            SwitchRow(
                title = "Утренняя сводка",
                subtitle = "Пары, работа, учёба и близкие дедлайны",
                checked = settings.morning,
                onChange = { on -> vm.updateReminders { it.copy(morning = on) } },
                time = settings.morningTime,
                onTimeClick = { editing = TimeField.Morning },
            )
        }
        item {
            SwitchRow(
                title = "Начало блоков учёбы",
                subtitle = "В уведомлении есть кнопка «Сделал»",
                checked = settings.blocks,
                onChange = { on -> vm.updateReminders { it.copy(blocks = on) } },
            )
        }
        item {
            SwitchRow(
                title = "Вечером: отметить сделанное",
                subtitle = "Только если что-то не отмечено. Не раньше конца последнего блока",
                checked = settings.evening,
                onChange = { on -> vm.updateReminders { it.copy(evening = on) } },
                time = settings.eveningTime,
                onTimeClick = { editing = TimeField.Evening },
            )
        }

        item {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.checkNow() }, enabled = !vm.checking) {
                    Text(if (vm.checking) "Проверяю…" else "Проверить сайт")
                }
                OutlinedButton(onClick = { vm.testNotification() }) {
                    Text("Пробное уведомление")
                }
            }
        }

        item { SectionTitle("Чтобы напоминания приходили вовремя") }
        item {
            Text(
                text = "Xiaomi с HyperOS сам закрывает приложения в фоне, и тогда напоминания " +
                    "опаздывают или не приходят. Достаточно один раз сделать три шага.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            ActionCard(
                text = "1. Разреши автозапуск: найди в списке Каждыйдневник и включи переключатель.",
                button = "Открыть автозапуск",
                onClick = { SystemSettings.openAutostart(context) },
            )
        }
        item {
            if (noBatteryLimits) {
                InfoNote("2. Экономия батареи не ограничивает приложение. Готово.")
            } else {
                ActionCard(
                    text = "2. Сними ограничения батареи. Если откроется страница приложения: " +
                        "«Контроль активности» → «Нет ограничений».",
                    button = "Снять ограничения",
                    onClick = { SystemSettings.requestNoBatteryLimits(context) },
                )
            }
        }
        item {
            InfoNote(
                "3. Закрепи приложение в недавних: открой список недавних приложений, " +
                    "зажми карточку Каждыйдневника и нажми на замок. Тогда кнопка «Очистить всё» его не закроет.",
            )
        }
        if (!exactAlarms) {
            item {
                ActionCard(
                    text = "Телефон не даёт ставить точные будильники, напоминания могут опаздывать на несколько минут.",
                    button = "Разрешить",
                    onClick = { SystemSettings.openExactAlarmSettings(context) },
                )
            }
        }

        item {
            Text(
                text = "Версия ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when (editing) {
        TimeField.Morning -> TimeDialog(
            title = "Утренняя сводка",
            initial = settings.morningTime,
            onDismiss = { editing = null },
            onConfirm = { time ->
                vm.updateReminders { it.copy(morningTime = time) }
                editing = null
            },
        )
        TimeField.Evening -> TimeDialog(
            title = "Вечернее напоминание",
            initial = settings.eveningTime,
            onDismiss = { editing = null },
            onConfirm = { time ->
                vm.updateReminders { it.copy(eveningTime = time) }
                editing = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    time: LocalTime? = null,
    onTimeClick: () -> Unit = {},
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
        if (time != null && checked) {
            TextButton(onClick = onTimeClick) { Text("Время: ${formatTime(time)} · изменить") }
        }
    }
}

@Composable
private fun ActionCard(text: String, button: String, onClick: () -> Unit, isError: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) colors.errorContainer else colors.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) colors.onErrorContainer else colors.onSurface,
            )
            FilledTonalButton(onClick = onClick) { Text(button) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Готово") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
