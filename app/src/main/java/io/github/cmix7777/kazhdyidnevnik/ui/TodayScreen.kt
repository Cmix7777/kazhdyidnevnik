package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.BuildConfig
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.DayItem
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.PlanBlock
import io.github.cmix7777.kazhdyidnevnik.data.PlanGenerator
import io.github.cmix7777.kazhdyidnevnik.data.Practice
import io.github.cmix7777.kazhdyidnevnik.data.WeatherAdvice
import io.github.cmix7777.kazhdyidnevnik.data.WeatherText
import io.github.cmix7777.kazhdyidnevnik.data.buildDay
import io.github.cmix7777.kazhdyidnevnik.data.formatMinutes
import io.github.cmix7777.kazhdyidnevnik.data.pluralLessons
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import io.github.cmix7777.kazhdyidnevnik.formatDate
import io.github.cmix7777.kazhdyidnevnik.formatDayTitle
import io.github.cmix7777.kazhdyidnevnik.formatStamp
import io.github.cmix7777.kazhdyidnevnik.formatTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    vm: ScheduleViewModel,
    extras: ExtrasViewModel,
    person: Person,
    modifier: Modifier = Modifier,
) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)
    val profile = person.profile
    val isOwner = person == vm.owner
    // План учёбы с галочками — только свой: отметки хранятся на телефоне владельца.
    val showPlan = isOwner && profile.hasPlan
    val schedule = vm.schedule(person)

    val todayWeek = schedule.weeks[weekStartFor(today)]
    val tomorrowWeek = schedule.weeks[weekStartFor(tomorrow)]
    val todayItems = buildDay(today, todayWeek?.lessons.orEmpty(), profile.shifts)
    val tomorrowItems = buildDay(tomorrow, tomorrowWeek?.lessons.orEmpty(), profile.shifts)
    val refreshing = vm.isLoading(person, 0)
    val forecast = extras.forecast

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            vm.refresh(person, 0)
            vm.refresh(person, 1)
            extras.refreshWeather()
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Шапка: чей день и дата крупно в две строки.
            item {
                val (dayName, dateText) = formatDayTitle(today).split(", ", limit = 2)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Pill(text = if (isOwner) "Сегодня" else "Сегодня у ${person.genitive}", accent = true)
                    TwoToneTitle(first = dayName, second = dateText)
                }
            }

            // Короткие факты дня таблетками.
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill(text = daySummary(todayItems))
                    if (profile.hasPractice) {
                        Practice.dayNumber(today)?.let { day ->
                            Pill(text = "практика: день $day из ${Practice.totalDays}", accent = true)
                        }
                    }
                }
            }

            if (isOwner && extras.update.release != null) {
                item { UpdateBanner(extras) }
            }

            schedule.error?.let { message ->
                item { InfoNote(message, isError = true) }
            }

            item {
                WeatherCard(
                    forecast = forecast,
                    updatedMillis = extras.weatherUpdatedMillis,
                    error = extras.weatherError,
                    loading = extras.weatherLoading,
                    date = today,
                    items = todayItems,
                    now = now,
                    onRetry = { extras.refreshWeather() },
                    leaveBefore = profile.leaveBeforeMinutes,
                )
            }

            item { SectionTitle("Расписание", accent = scheduleAccent(todayItems)) }
            when {
                todayWeek == null && refreshing -> item { InfoNote("Загружаю расписание…") }
                todayWeek == null -> item {
                    InfoNote("Расписание ещё не загружено. Потяни экран вниз, чтобы обновить.")
                }
                todayItems.isEmpty() -> item { InfoNote("Сегодня ни пар, ни работы. Свободный день.") }
                else -> items(todayItems) { DayItemCard(it, now.toLocalTime()) }
            }

            // План учёбы: только у владельца телефона и только если план есть.
            if (showPlan && today.isBefore(PlanGenerator.firstDay)) {
                val note = if (PlanGenerator.firstDay == tomorrow) {
                    "План учёбы начинается завтра, он показан ниже."
                } else {
                    "План учёбы начинается ${formatDate(PlanGenerator.firstDay)}."
                }
                item { SectionTitle("План учёбы") }
                item { InfoNote(note) }
            } else if (showPlan && todayWeek != null) {
                val plan = PlanGenerator.planFor(today, todayItems)
                val doneMinutes = plan.filter { it.key(today) in vm.done }.sumOf { it.minutes }
                val totalMinutes = plan.sumOf { it.minutes }
                item {
                    SectionTitle(
                        text = "План учёбы",
                        accent = if (doneMinutes > 0) {
                            "${formatMinutes(doneMinutes)} из ${formatMinutes(totalMinutes)}"
                        } else {
                            formatMinutes(totalMinutes)
                        },
                        badge = if (plan.isNotEmpty() && doneMinutes == totalMinutes) "всё сделано" else null,
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

            item {
                SectionTitle(
                    text = "Завтра",
                    accent = formatDayTitle(tomorrow).substringBefore(',').lowercase(),
                )
            }
            val tomorrowWeather = forecast?.day(tomorrow)
            if (forecast != null && tomorrowWeather != null) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        WeatherPill(tomorrowWeather)
                        WeatherAdvice.alerts(tomorrowWeather, forecast.day(today)).forEach { AlertPill(it) }
                    }
                }
                val tomorrowTrips = WeatherAdvice.tripWeather(tomorrow, tomorrowItems, forecast, profile.leaveBeforeMinutes)
                WeatherAdvice.advice(tomorrowTrips, tomorrowWeather)?.let { advice ->
                    val leave = tomorrowTrips.first()
                    item {
                        InfoNote(
                            "Завтра выход в ${formatTime(leave.trip.time)}: " +
                                "${WeatherText.temperature(leave.hour.temperature)}, ${WeatherText.condition(leave.hour.code)}. $advice",
                        )
                    }
                }
            }
            when {
                tomorrowWeek == null -> item { InfoNote("Расписание на завтра ещё не загружено.") }
                tomorrowItems.isEmpty() -> item { InfoNote("Завтра ни пар, ни работы.") }
                else -> items(tomorrowItems) { DayItemCard(it, now = null, dim = true) }
            }
            if (showPlan && tomorrowWeek != null) {
                val tomorrowPlan = PlanGenerator.planFor(tomorrow, tomorrowItems)
                if (tomorrowPlan.isNotEmpty()) item { PlanPreview(tomorrowPlan) }
            }

            item {
                Text(
                    text = footer(todayWeek?.fetchedAtMillis, profile.scheduleSource),
                    modifier = Modifier.fillParentMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
        }
    }
}

/** Короткий план учёбы на завтра: время и название блоков. */
@Composable
private fun PlanPreview(plan: List<PlanBlock>) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon = R.drawable.ic_nav_progress)
            Column {
                Text(text = "Учёба завтра", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(
                    text = formatMinutes(plan.sumOf { it.minutes }),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        }
        GlassDivider()
        plan.forEach { block ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${formatTime(block.start)}–${formatTime(block.end)}",
                    modifier = Modifier.width(92.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Lavender,
                )
                Text(text = block.title, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
            }
        }
    }
}

private fun daySummary(items: List<DayItem>): String {
    val lessons = items.count { it is DayItem.LessonItem }
    val work = items.any { it is DayItem.WorkItem }
    return when {
        lessons == 0 && !work -> "свободный день"
        lessons == 0 -> "пар нет, есть смена"
        work -> "${pluralLessons(lessons)} и смена"
        else -> pluralLessons(lessons)
    }
}

/** Подпись к разделу «Расписание»: когда начинается и заканчивается день. */
private fun scheduleAccent(items: List<DayItem>): String? {
    if (items.isEmpty()) return null
    return "${formatTime(items.first().start)}–${formatTime(items.maxOf { it.end })}"
}

private fun footer(fetchedAt: Long?, source: String): String {
    val updated = fetchedAt?.let { "расписание обновлено ${formatStamp(it)}" } ?: "расписание ещё не обновлялось"
    return "Версия ${BuildConfig.VERSION_NAME} · $updated · $source"
}
