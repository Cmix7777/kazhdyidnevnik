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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.Practice
import io.github.cmix7777.kazhdyidnevnik.data.WorkSchedule
import io.github.cmix7777.kazhdyidnevnik.data.buildDay
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import io.github.cmix7777.kazhdyidnevnik.formatDayTitle
import io.github.cmix7777.kazhdyidnevnik.formatWeekRange

private const val MIN_OFFSET = -4
private const val MAX_OFFSET = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(vm: ScheduleViewModel, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val today = now.toLocalDate()
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val monday = weekStartFor(today).plusWeeks(offset.toLong())
    val week = vm.weeks[monday]
    val refreshing = vm.isLoading(offset)

    LaunchedEffect(offset) { vm.ensureWeek(offset) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { offset-- }, enabled = offset > MIN_OFFSET) { Text("‹ Назад") }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatWeekRange(monday),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = when (offset) {
                        0 -> "эта неделя"
                        1 -> "следующая неделя"
                        -1 -> "прошлая неделя"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { offset++ }, enabled = offset < MAX_OFFSET) { Text("Вперёд ›") }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh(offset) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (week == null) {
                    item {
                        InfoNote(
                            if (refreshing) "Загружаю расписание…"
                            else "Эта неделя ещё не загружена. Потяни экран вниз, чтобы обновить.",
                        )
                    }
                } else {
                    if (week.lessons.isEmpty()) {
                        item {
                            InfoNote(
                                if (Practice.dayNumber(monday) != null || Practice.dayNumber(monday.plusDays(6)) != null)
                                    "На этой неделе пар нет: практика."
                                else "На этой неделе пар в расписании нет.",
                            )
                        }
                    }
                    for (dayIndex in 0..6) {
                        val date = monday.plusDays(dayIndex.toLong())
                        val dayItems = buildDay(date, week.lessons, WorkSchedule.default)
                        val isToday = date == today
                        item(key = "title-$date") {
                            SectionTitle(formatDayTitle(date) + if (isToday) " · сегодня" else "")
                        }
                        if (dayItems.isEmpty()) {
                            item(key = "empty-$date") {
                                Text(
                                    text = "Ни пар, ни работы",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(dayItems) { DayItemCard(it, now = if (isToday) now.toLocalTime() else null) }
                        }
                    }
                }
            }
        }
    }
}
