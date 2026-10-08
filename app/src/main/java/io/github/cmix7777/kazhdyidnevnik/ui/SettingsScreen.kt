package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cmix7777.kazhdyidnevnik.BuildConfig
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.Person
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
    val owner = vm.owner ?: Person.AIZAT
    val partner = owner.partner
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

        item { SectionTitle("Чей это телефон") }
        item {
            GlassCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Свои уведомления приходят тому, чей телефон, и его страница открывается первой.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Person.entries.forEach { person ->
                        if (person == owner) {
                            Pill(text = person.shortName, accent = true)
                        } else {
                            GhostButton(text = person.shortName, onClick = { vm.chooseOwner(person) }, small = true)
                        }
                    }
                }
            }
        }

        item { SectionTitle("Уведомления") }
        item {
            GlassCard(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                MatrixHeader(partnerTitle = partner.genitive.replaceFirstChar { it.uppercase() })
                GlassDivider()
                MatrixRow(
                    title = "Изменения в расписании",
                    subtitle = "Сайты проверяются в 23:00, в 6:30 и примерно раз в 3 часа",
                    mine = settings.changes,
                    onMine = { on -> vm.updateReminders { it.copy(changes = on) } },
                    theirs = settings.partner.changes,
                    onTheirs = { on -> vm.updateReminders { it.copy(partner = it.partner.copy(changes = on)) } },
                )
                GlassDivider()
                MatrixRow(
                    title = "Утренняя сводка",
                    subtitle = "Пары, работа, погода и близкие даты",
                    mine = settings.morning,
                    onMine = { on -> vm.updateReminders { it.copy(morning = on) } },
                    theirs = settings.partner.morning,
                    onTheirs = { on -> vm.updateReminders { it.copy(partner = it.partner.copy(morning = on)) } },
                    time = settings.morningTime.takeIf { settings.morning || settings.partner.morning },
                    onTimeClick = { editing = TimeField.Morning },
                )
                GlassDivider()
                MatrixRow(
                    title = "Погода на завтра",
                    subtitle = "В 21:00: погода при выходе и на обратном пути, что надеть",
                    mine = settings.weather,
                    onMine = { on -> vm.updateReminders { it.copy(weather = on) } },
                    theirs = settings.partner.weather,
                    onTheirs = { on -> vm.updateReminders { it.copy(partner = it.partner.copy(weather = on)) } },
                )
                if (owner.profile.hasPlan || partner.profile.hasPlan) {
                    GlassDivider()
                    MatrixRow(
                        title = "Начало блоков учёбы",
                        subtitle = "В своём уведомлении есть кнопка «Сделал»",
                        mine = settings.blocks.takeIf { owner.profile.hasPlan },
                        onMine = { on -> vm.updateReminders { it.copy(blocks = on) } },
                        theirs = settings.partner.study.takeIf { partner.profile.hasPlan },
                        onTheirs = { on -> vm.updateReminders { it.copy(partner = it.partner.copy(study = on)) } },
                    )
                }
                if (owner.profile.hasPlan) {
                    GlassDivider()
                    MatrixRow(
                        title = "Вечером: отметить сделанное",
                        subtitle = "Только если что-то не отмечено. Не раньше конца последнего блока",
                        mine = settings.evening,
                        onMine = { on -> vm.updateReminders { it.copy(evening = on) } },
                        theirs = null,
                        onTheirs = {},
                        time = settings.eveningTime.takeIf { settings.evening },
                        onTimeClick = { editing = TimeField.Evening },
                    )
                }
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
        when (SystemSettings.maker) {
            SystemSettings.Maker.Samsung -> {
                item {
                    Text(
                        text = "Samsung усыпляет приложения, которые давно не открывали, и тогда напоминания " +
                            "опаздывают или не приходят. Достаточно один раз сделать три шага.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                    )
                }
                item {
                    if (noBatteryLimits) {
                        InfoNote("1. Батарея: «Без ограничений». Готово.")
                    } else {
                        ActionCard(
                            text = "1. Разреши работу без ограничений батареи: в окне нажми «Разрешить». " +
                                "Если откроется страница приложения: «Аккумулятор» → «Без ограничений».",
                            button = "Снять ограничения",
                            onClick = { SystemSettings.requestNoBatteryLimits(context) },
                        )
                    }
                }
                item {
                    ActionCard(
                        text = "2. Добавь в «никогда не засыпающие»: «Ограничения фонового использования» → " +
                            "«Приложения, которые никогда не переходят в спящий режим» → «+» → Каждыйдневник → «Добавить».",
                        button = "Открыть аккумулятор",
                        onClick = { SystemSettings.openSamsungBattery(context) },
                    )
                }
                item {
                    InfoNote(
                        "3. Чтобы «Закрыть все» в недавних не выгружало приложение: открой недавние, " +
                            "нажми на значок Каждыйдневника над карточкой и выбери «Не закрывать».",
                    )
                }
            }
            SystemSettings.Maker.Xiaomi -> {
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
            }
            SystemSettings.Maker.Other -> {
                item {
                    if (noBatteryLimits) {
                        InfoNote("Экономия батареи не ограничивает приложение. Готово.")
                    } else {
                        ActionCard(
                            text = "Сними ограничения батареи, чтобы телефон не усыплял приложение в фоне.",
                            button = "Снять ограничения",
                            onClick = { SystemSettings.requestNoBatteryLimits(context) },
                        )
                    }
                }
            }
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

private val SwitchColumn = 60.dp

/** Заголовки столбцов: свои уведомления и про второго человека. */
@Composable
private fun MatrixHeader(partnerTitle: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Что присылать",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextFaint,
        )
        listOf("Мои", partnerTitle).forEach { title ->
            Text(
                text = title,
                modifier = Modifier.width(SwitchColumn),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Lavender,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Строка таблицы уведомлений. null вместо значения — у этого человека такого нет. */
@Composable
private fun MatrixRow(
    title: String,
    subtitle: String,
    mine: Boolean?,
    onMine: (Boolean) -> Unit,
    theirs: Boolean?,
    onTheirs: (Boolean) -> Unit,
    time: LocalTime? = null,
    onTimeClick: () -> Unit = {},
) {
    Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            MatrixSwitch(mine, onMine)
            MatrixSwitch(theirs, onTheirs)
        }
        if (time != null) {
            GhostButton(text = "в ${formatTime(time)} · изменить время", onClick = onTimeClick, small = true)
        }
    }
}

@Composable
private fun MatrixSwitch(checked: Boolean?, onChange: (Boolean) -> Unit) {
    Box(modifier = Modifier.width(SwitchColumn), contentAlignment = Alignment.Center) {
        if (checked == null) {
            Text(text = "—", style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
        } else {
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
