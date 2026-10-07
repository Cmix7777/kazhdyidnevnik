package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.Curriculum
import io.github.cmix7777.kazhdyidnevnik.data.MockExams
import io.github.cmix7777.kazhdyidnevnik.data.PlanKind
import io.github.cmix7777.kazhdyidnevnik.data.ProgressStats
import io.github.cmix7777.kazhdyidnevnik.data.Topic
import io.github.cmix7777.kazhdyidnevnik.data.formatMinutes
import io.github.cmix7777.kazhdyidnevnik.data.plural
import io.github.cmix7777.kazhdyidnevnik.data.pluralDays
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import io.github.cmix7777.kazhdyidnevnik.formatDate

private const val SESSIONS_FOR_DONE = 3

@Composable
fun ProgressScreen(vm: ScheduleViewModel, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val done = vm.done.toMap()
    val monday = weekStartFor(today)
    val week = Curriculum.weekNumber(today)

    val streak = ProgressStats.pddStreak(done, today)
    val history = ProgressStats.pddHistory(done, today)
    val thisWeek = ProgressStats.minutesInWeek(done, monday)
    val lastWeek = ProgressStats.minutesInWeek(done, monday.minusWeeks(1))
    val mocks = ProgressStats.count(done, PlanKind.MOCK)
    val nextMock = MockExams.next(today)
    val csharp = ProgressStats.count(done, PlanKind.CSHARP)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Прогресс", style = MaterialTheme.typography.headlineSmall) }

        item {
            StatCard(title = "Билеты ПДД") {
                Text(
                    text = if (streak > 0) "${pluralDays(streak.toLong())} подряд" else "Серии пока нет",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    history.forEach { solved ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (solved) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        )
                    }
                }
                Caption("последние 14 дней, сегодня справа")
            }
        }

        item {
            StatCard(title = "Учёба на этой неделе") {
                Text(
                    text = formatMinutes(thisWeek),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Caption("прошлая неделя: ${formatMinutes(lastWeek)}")
            }
        }

        item {
            StatCard(title = "Пробные демоэкзамены") {
                Text(
                    text = "$mocks " + plural(mocks.toLong(), "пробник", "пробника", "пробников"),
                    style = MaterialTheme.typography.titleLarge,
                )
                nextMock?.let { Caption("следующий: ${formatDate(it)}") }
            }
        }

        if (!today.isAfter(Curriculum.csharpDeadline) || csharp > 0) {
            item {
                StatCard(title = "Подготовка к сдаче сайта на C#") {
                    Text(
                        text = "$csharp " + plural(csharp.toLong(), "занятие", "занятия", "занятий"),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }

        item { SectionTitle("Тестирование (QA) с нуля") }
        items(Curriculum.qa, key = { "qa-${it.number}" }) { topic ->
            TopicRow(topic, ProgressStats.topicSessions(done, PlanKind.QA, topic.number), week)
        }

        item { SectionTitle("Laravel к демоэкзамену") }
        items(Curriculum.laravel, key = { "laravel-${it.number}" }) { topic ->
            TopicRow(topic, ProgressStats.topicSessions(done, PlanKind.LARAVEL, topic.number), week)
        }

        item {
            Caption(
                "Тема считается пройденной, когда по ней отмечено $SESSIONS_FOR_DONE занятия. " +
                    "После последней темы программа идёт по второму кругу как повторение.",
            )
        }
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun TopicRow(topic: Topic, sessions: Int, currentWeek: Int) {
    val colors = MaterialTheme.colorScheme
    val isCurrent = topic.number == currentWeek
    val isDone = sessions >= SESSIONS_FOR_DONE
    val status = when {
        isDone -> "пройдена"
        isCurrent -> "сейчас"
        topic.number < currentWeek -> "занятий: $sessions"
        else -> "неделя ${topic.number}"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    when {
                        isDone -> colors.primary
                        isCurrent -> colors.tertiary
                        else -> colors.surfaceVariant
                    },
                ),
        )
        Text(
            text = "${topic.number}. ${topic.title}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (isCurrent) colors.tertiary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
