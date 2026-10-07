package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cmix7777.kazhdyidnevnik.BuildConfig
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.formatStamp
import io.github.cmix7777.kazhdyidnevnik.formatTime
import io.github.cmix7777.kazhdyidnevnik.notify.Notifier
import io.github.cmix7777.kazhdyidnevnik.notify.SystemSettings
import io.github.cmix7777.kazhdyidnevnik.service.Backups
import java.time.LocalTime

private enum class TimeField { Morning, Evening }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: ScheduleViewModel,
    extras: ExtrasViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) extras.restore(uri) { vm.reloadSettings() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Pill(text = "Каждыйдневник ${BuildConfig.VERSION_NAME}", accent = true)
                    TwoToneTitle(first = "Настройки", second = "уведомления и копии")
                }
                GlassIconButton(icon = R.drawable.ic_arrow_back, contentDescription = "Назад", onClick = onBack)
            }
        }

        if (!notificationsOn) {
            item {
                ActionCard(
                    text = "Уведомления для приложения выключены, напоминания не придут.",
                    button = "Включить уведомления",
                    danger = true,
                    onClick = { SystemSettings.openNotificationSettings(context) },
                )
            }
        }

        item { SectionTitle("Уведомления") }
        item {
            GlassCard(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                SwitchRow(
                    title = "Изменения в расписании",
                    subtitle = "Сайт проверяется в 23:00, в 6:30 и примерно раз в 3 часа",
                    checked = settings.changes,
                    onChange = { on -> vm.updateReminders { it.copy(changes = on) } },
                )
                GlassDivider()
                SwitchRow(
                    title = "Утренняя сводка",
                    subtitle = "Пары, работа, погода, учёба и близкие дедлайны",
                    checked = settings.morning,
                    onChange = { on -> vm.updateReminders { it.copy(morning = on) } },
                    time = settings.morningTime,
                    onTimeClick = { editing = TimeField.Morning },
                )
                GlassDivider()
                SwitchRow(
                    title = "Начало блоков учёбы",
                    subtitle = "В уведомлении есть кнопка «Сделал»",
                    checked = settings.blocks,
                    onChange = { on -> vm.updateReminders { it.copy(blocks = on) } },
                )
                GlassDivider()
                SwitchRow(
                    title = "Вечером: отметить сделанное",
                    subtitle = "Только если что-то не отмечено. Не раньше конца последнего блока",
                    checked = settings.evening,
                    onChange = { on -> vm.updateReminders { it.copy(evening = on) } },
                    time = settings.eveningTime,
                    onTimeClick = { editing = TimeField.Evening },
                )
            }
        }
        item {
            Text(text = status, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GhostButton(
                    text = if (vm.checking) "Проверяю…" else "Проверить сайт",
                    onClick = { vm.checkNow() },
                    enabled = !vm.checking,
                )
                GhostButton(text = "Пробное уведомление", onClick = { vm.testNotification() })
            }
        }

        item { SectionTitle("Обновления") }
        if (extras.update.release != null) {
            item { UpdateBanner(extras) }
        }
        item {
            val state = extras.update
            val text = when {
                state is UpdateState.Checking -> "Проверяю, есть ли новая версия…"
                state is UpdateState.UpToDate -> "Установлена последняя версия ${BuildConfig.VERSION_NAME}."
                state is UpdateState.Failed && state.release == null -> "Не получилось проверить: ${state.message}."
                else -> "Установлена версия ${BuildConfig.VERSION_NAME}."
            }
            val checked = extras.lastUpdateCheckMillis.takeIf { it > 0 }
                ?.let { "Последняя проверка: ${formatStamp(it)}. Приложение само проверяет раз в полдня." }
            GlassCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = text, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                checked?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted) }
                GhostButton(
                    text = "Проверить обновления",
                    onClick = { extras.checkUpdate() },
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                )
            }
        }

        item { SectionTitle("Резервная копия") }
        item {
            val last = extras.lastBackupMillis.takeIf { it > 0 }
                ?.let { "Последняя копия: ${formatStamp(it)}." }
                ?: "Копий пока не было."
            GlassCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Раз в день отметки и настройки сами сохраняются в файл в папке " +
                        "«Загрузки/${Backups.FOLDER}». Он останется, даже если удалить приложение. " +
                        "После переустановки или на новом телефоне нажми «Восстановить» и выбери этот файл.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                )
                Text(text = last, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GhostButton(text = "Сохранить сейчас", onClick = { extras.saveBackupNow() })
                    GhostButton(text = "Восстановить", onClick = { restoreLauncher.launch(arrayOf("*/*")) })
                }
            }
        }
        extras.backupMessage?.let { message ->
            item { InfoNote(message) }
        }

        item { SectionTitle("Чтобы напоминания приходили вовремя") }
        item {
            Text(
                text = "Xiaomi с HyperOS сам закрывает приложения в фоне, и тогда напоминания " +
                    "опаздывают или не приходят. Достаточно один раз сделать три шага.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
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
                color = Palette.TextFaint,
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
    Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        if (time != null && checked) {
            GhostButton(text = "в ${formatTime(time)} · изменить время", onClick = onTimeClick, small = true)
        }
    }
}

@Composable
private fun ActionCard(text: String, button: String, onClick: () -> Unit, danger: Boolean = false) {
    GlassCard(tone = if (danger) CardTone.Danger else CardTone.Normal, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (danger) MaterialTheme.colorScheme.onErrorContainer else Palette.Text,
        )
        PillButton(text = button, onClick = onClick)
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
        containerColor = Palette.CardTop,
    )
}
