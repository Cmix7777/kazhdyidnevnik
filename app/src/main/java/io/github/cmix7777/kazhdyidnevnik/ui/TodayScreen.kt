package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.BuildConfig
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.DayItem
import io.github.cmix7777.kazhdyidnevnik.data.PlanBlock
import io.github.cmix7777.kazhdyidnevnik.data.PlanGenerator
import io.github.cmix7777.kazhdyidnevnik.data.Practice
import io.github.cmix7777.kazhdyidnevnik.data.formatMinutes
import io.github.cmix7777.kazhdyidnevnik.data.WeatherText
import io.github.cmix7777.kazhdyidnevnik.data.WorkSchedule
import io.github.cmix7777.kazhdyidnevnik.data.buildDay
import io.github.cmix7777.kazhdyidnevnik.data.pluralLessons
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import io.github.cmix7777.kazhdyidnevnik.formatDate
import io.github.cmix7777.kazhdyidnevnik.formatDayTitle
import io.github.cmix7777.kazhdyidnevnik.formatTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: ScheduleViewModel,
    extras: ExtrasViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)

    val todayWeek = vm.weeks[weekStartFor(today)]
    val tomorrowWeek = vm.weeks[weekStartFor(tomorrow)]
    val todayItems = buildDay(today, todayWeek?.lessons.orEmpty(), WorkSchedule.default)
    val tomorrowItems = buildDay(tomorrow, tomorrowWeek?.lessons.orEmpty(), WorkSchedule.default)
    val refreshing = vm.isLoading(0)

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            vm.refresh(0)
            vm.refresh(1)
            extras.refreshWeather()
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.Top) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = formatDayTitle(today), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = daySummary(todayItems),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        extras.weather.firstOrNull { it.date == today }?.let { weather ->
                            val advice = WeatherText.advice(weather)?.let { " · $it" }.orEmpty()
                            Text(
                                text = "Ижевск: ${WeatherText.short(weather)}$advice",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Настройки")
                    }
                }
            }

            if (extras.update.release != null) {
                item { UpdateBanner(extras) }
            }

            Practice.dayNumber(today)?.let { day ->
                item { InfoNote("Практика: день $day из ${Practice.totalDays}") }
            }

            vm.error?.let { message ->
                item { InfoNote(message, isError = true) }
            }

            when {
                todayWeek == null && refreshing -> item { InfoNote("Загружаю расписание…") }
                todayWeek == null -> item {
                    InfoNote("Расписание ещё не загружено. Потяни экран вниз, чтобы обновить.")
                }
                todayItems.isEmpty() -> item { InfoNote("Сегодня ни пар, ни работы. Свободный день.") }
                else -> items(todayItems) { DayItemCard(it, now.toLocalTime()) }
            }

            if (today.isBefore(PlanGenerator.firstDay)) {
                val note = if (PlanGenerator.firstDay == tomorrow) {
                    "План учёбы начинается завтра, он показан ниже."
                } else {
                    "План учёбы начинается ${formatDate(PlanGenerator.firstDay)}."
                }
                item { SectionTitle("План учёбы") }
                item { InfoNote(note) }
            } else if (todayWeek != null) {
                val plan = PlanGenerator.planFor(today, todayItems)
                val doneMinutes = plan.filter { it.key(today) in vm.done }.sumOf { it.minutes }
                val totalMinutes = plan.sumOf { it.minutes }
                item {
                    SectionTitle(
                        "План учёбы · " + if (doneMinutes > 0) {
                            "${formatMinutes(doneMinutes)} из ${formatMinutes(totalMinutes)}"
                        } else {
                            formatMinutes(totalMinutes)
                        },
                    )
                }
                if (plan.isEmpty()) {
                    item { InfoNote("Сегодня свободного времени почти нет, отдыхай.") }
                } else {
                    items(plan, key = { it.key(today) }) { block ->
                        PlanBlockCard(
                            block = block,
                            done = block.key(today) in vm.done,
                            onToggle = { vm.toggleDone(today, block) },
                        )
                    }
                }
            }

            item { SectionTitle("Завтра: ${formatDayTitle(tomorrow).substringBefore(',')}") }
            when {
                tomorrowWeek == null -> item { InfoNote("Расписание на завтра ещё не загружено.") }
                tomorrowItems.isEmpty() -> item { InfoNote("Завтра ни пар, ни работы.") }
                else -> items(tomorrowItems) { DayItemCard(it, now = null) }
            }
            if (tomorrowWeek != null) {
                val tomorrowPlan = PlanGenerator.planFor(tomorrow, tomorrowItems)
                if (tomorrowPlan.isNotEmpty()) item { PlanPreview(tomorrowPlan) }
            }

            item {
                Text(
                    text = footer(todayWeek?.fetchedAtMillis),
                    modifier = Modifier.fillParentMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Короткий план учёбы на завтра: время и название блоков. */
@Composable
private fun PlanPreview(plan: List<PlanBlock>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Учёба завтра · ${formatMinutes(plan.sumOf { it.minutes })}",
                style = MaterialTheme.typography.titleSmall,
            )
            plan.forEach { block ->
                Text(
                    text = "${formatTime(block.start)}–${formatTime(block.end)}  ${block.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun daySummary(items: List<DayItem>): String {
    val lessons = items.count { it is DayItem.LessonItem }
    val work = items.any { it is DayItem.WorkItem }
    return when {
        lessons == 0 && !work -> "Свободный день"
        lessons == 0 -> "Пар нет, есть смена"
        work -> "${pluralLessons(lessons)} и смена"
        else -> pluralLessons(lessons)
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("d.MM HH:mm")

private fun footer(fetchedAt: Long?): String {
    val updated = fetchedAt?.let {
        "расписание обновлено " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timeFormat)
    } ?: "расписание ещё не обновлялось"
    return "Версия ${BuildConfig.VERSION_NAME} · $updated"
}
