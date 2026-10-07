package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.R
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill(text = "Прогресс", accent = true)
                TwoToneTitle(first = "Как идёт", second = "учёба и привычки")
            }
        }

        item {
            GlassCard(tone = if (streak > 0) CardTone.Highlight else CardTone.Normal) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(icon = R.drawable.ic_check)
                    Column {
                        Text("Билеты ПДД", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
                        Text(
                            text = if (streak > 0) "${pluralDays(streak.toLong())} подряд" else "Серии пока нет",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Palette.Text,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    history.forEach { solved ->
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
                Caption("последние 14 дней, сегодня справа")
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    title = "Эта неделя",
                    value = formatMinutes(thisWeek),
                    caption = "прошлая: ${formatMinutes(lastWeek)}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "Пробники",
                    value = mocks.toString(),
                    caption = nextMock?.let { "следующий ${formatDate(it).substringBeforeLast(' ')}" }
                        ?: plural(mocks.toLong(), "пробник", "пробника", "пробников"),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!today.isAfter(Curriculum.csharpDeadline) || csharp > 0) {
            item {
                GlassCard {
                    Text("Сдача сайта на C#", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
                    Text(
                        text = "$csharp " + plural(csharp.toLong(), "занятие", "занятия", "занятий"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Palette.Text,
                    )
                    Caption("подготовка до ${formatDate(Curriculum.csharpDeadline)}")
                }
            }
        }

        item { SectionTitle("Тестирование", accent = "с нуля") }
        item { TopicList(Curriculum.qa, PlanKind.QA, done, week) }

        item { SectionTitle("Laravel", accent = "к демоэкзамену") }
        item { TopicList(Curriculum.laravel, PlanKind.LARAVEL, done, week) }

        item {
            Caption(
                "Тема считается пройденной, когда по ней отмечено $SESSIONS_FOR_DONE занятия. " +
                    "После последней темы программа идёт по второму кругу как повторение.",
            )
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
        Caption(caption)
    }
}

@Composable
private fun TopicList(topics: List<Topic>, kind: PlanKind, done: Map<String, Int>, currentWeek: Int) {
    GlassCard(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        topics.forEachIndexed { index, topic ->
            if (index > 0) GlassDivider()
            TopicRow(topic, ProgressStats.topicSessions(done, kind, topic.number), currentWeek)
        }
    }
}

@Composable
private fun TopicRow(topic: Topic, sessions: Int, currentWeek: Int) {
    val isCurrent = topic.number == currentWeek
    val isDone = sessions >= SESSIONS_FOR_DONE
    val status = when {
        isDone -> "пройдена"
        isCurrent -> "сейчас"
        topic.number < currentWeek -> "занятий: $sessions"
        else -> "неделя ${topic.number}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .then(
                    when {
                        isDone -> Modifier.background(Palette.Violet)
                        isCurrent -> Modifier.background(Palette.Lavender)
                        else -> Modifier.border(1.dp, Palette.BorderStrong, CircleShape)
                    },
                ),
        )
        Text(
            text = "${topic.number}. ${topic.title}",
            modifier = Modifier.weight(1f),
            style = if (isCurrent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = when {
                isCurrent -> Palette.Text
                isDone -> Palette.TextMuted
                else -> Palette.TextMuted
            },
        )
        if (isCurrent) {
            Pill(text = status, accent = true)
        } else {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDone) Palette.Lavender else Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
}
