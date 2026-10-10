package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.MoneyEntry
import io.github.cmix7777.kazhdyidnevnik.data.MoneyFormat
import io.github.cmix7777.kazhdyidnevnik.data.MoneyStats
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.formatDate
import io.github.cmix7777.kazhdyidnevnik.formatShortDay
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class Period(val title: String, val days: Long?) {
    Week("Неделя", 7),
    Month("Месяц", 30),
    All("Всё время", null),
}

private data class MoneyEdit(val entry: MoneyEntry?, val income: Boolean)

private val shortMonths = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private val monthTitles = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

private fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${shortMonths[date.monthValue - 1]}"

/** Бюджет владельца телефона: плюс и минус, остаток по дням на графике, куда уходят деньги. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetScreen(diary: DiaryViewModel, person: Person, isOwner: Boolean, modifier: Modifier = Modifier) {
    if (!isOwner) {
        BudgetElsewhere(person, modifier)
        return
    }
    val now by rememberNow()
    val today = now.toLocalDate()
    val entries = diary.diary.money
    var period by rememberSaveable { mutableStateOf(Period.Month) }
    var editing by remember { mutableStateOf<MoneyEdit?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val balance = MoneyStats.balance(entries, today)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill(text = "Бюджет", accent = true)
                TwoToneTitle(first = "Остаток", second = MoneyFormat.format(balance))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyButton(
                    symbol = "+",
                    label = "Доход",
                    filled = true,
                    onClick = { editing = MoneyEdit(null, income = true) },
                    modifier = Modifier.weight(1f),
                )
                MoneyButton(
                    symbol = "−",
                    label = "Трата",
                    filled = false,
                    onClick = { editing = MoneyEdit(null, income = false) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (entries.isEmpty()) {
            item {
                InfoNote(
                    "Нажми «+», чтобы записать, сколько денег сейчас или сколько пришло, и «−», когда что-то " +
                        "тратишь. Здесь появится график остатка по дням и статистика, на что уходят деньги.",
                )
            }
        } else {
            item {
                val first = entries.minOf { it.date.toEpochDay() }.let { LocalDate.ofEpochDay(it) }
                val last = maxOf(today, entries.maxOf { it.date.toEpochDay() }.let { LocalDate.ofEpochDay(it) })
                val to = if (period == Period.All) last else today
                val from = period.days?.let { today.minusDays(it - 1) } ?: minOf(first, to.minusDays(6))
                ChartCard(
                    points = MoneyStats.balanceByDay(entries, from, to),
                    period = period,
                    onPeriod = { period = it },
                    today = today,
                )
            }
            item { MonthCard(entries, today) }

            val groups = entries
                .sortedWith(compareByDescending<MoneyEntry> { it.date.toEpochDay() }.thenByDescending { it.createdAt })
                .let { if (showAll) it else it.take(HISTORY_LIMIT) }
                .groupBy { it.date }
            item { SectionTitle("История") }
            groups.forEach { (date, list) ->
                item(key = "day-$date") {
                    val net = list.sumOf { it.signed }
                    SectionTitle(text = formatShortDay(date), accent = MoneyFormat.format(net, sign = true))
                }
                item(key = "list-$date") {
                    GlassCard(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        list.forEachIndexed { index, entry ->
                            if (index > 0) GlassDivider()
                            EntryRow(entry, onClick = { editing = MoneyEdit(entry, entry.income) })
                        }
                    }
                }
            }
            if (!showAll && entries.size > HISTORY_LIMIT) {
                item { GhostButton(text = "Показать всю историю (${entries.size})", onClick = { showAll = true }) }
            }
        }
        item {
            Text(
                text = "Бюджет хранится только на этом телефоне и попадает в резервную копию в «Загрузках».",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }

    editing?.let { edit ->
        MoneyEditor(
            initial = edit.entry,
            income = edit.income,
            onDismiss = { editing = null },
            onSave = {
                diary.saveMoney(it)
                editing = null
            },
            onDelete = {
                edit.entry?.let { entry -> diary.deleteMoney(entry.id) }
                editing = null
            },
        )
    }
}

private const val HISTORY_LIMIT = 40

@Composable
private fun BudgetElsewhere(person: Person, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Pill(text = "Бюджет", accent = true)
                TwoToneTitle(first = "Бюджет", second = person.genitive)
            }
        }
        item {
            val whose = if (person == Person.NASTYA) "её" else "его"
            InfoNote("Бюджет ${person.genitive} личный и хранится только на $whose телефоне.")
        }
    }
}

@Composable
private fun MoneyButton(symbol: String, label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(if (filled) Brush.linearGradient(listOf(Palette.Violet, Palette.VioletDeep)) else SolidColor(Palette.Chip))
            .border(1.dp, if (filled) Color.Transparent else Palette.BorderStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = symbol, style = MaterialTheme.typography.headlineMedium, color = if (filled) Color.White else Palette.Text)
        Text(text = label, style = MaterialTheme.typography.titleMedium, color = if (filled) Color.White else Palette.Text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCard(points: List<Pair<LocalDate, Long>>, period: Period, onPeriod: (Period) -> Unit, today: LocalDate) {
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    GlassCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Остаток по дням", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { option ->
                SelectPill(text = option.title, selected = period == option, onClick = { onPeriod(option) })
            }
        }
        if (points.isNotEmpty()) {
            val index = (selected ?: points.indexOfLast { !it.first.isAfter(today) }.takeIf { it >= 0 } ?: points.lastIndex)
                .coerceIn(0, points.lastIndex)
            val shown = points[index]
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = MoneyFormat.format(shown.second), style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                Text(
                    text = if (shown.first == today) "сегодня" else "на конец дня ${formatDate(shown.first)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
            BalanceChart(
                points = points,
                selected = index,
                onSelect = { selected = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
            )
            Text(
                text = "Проведи пальцем по графику, чтобы увидеть остаток в любой день.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
        }
    }
}

/** Линия остатка по дням: тонкая линия, заливка под ней, отмеченный день с вертикальной линией. */
@Composable
private fun BalanceChart(
    points: List<Pair<LocalDate, Long>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Palette.TextFaint)
    val gridColor = Color.White.copy(alpha = 0.07f)
    val crossColor = Color.White.copy(alpha = 0.25f)

    fun indexAt(x: Float, width: Int): Int =
        if (points.size <= 1 || width <= 0) 0
        else (x / width * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)

    Canvas(
        modifier = modifier
            .pointerInput(points) {
                detectTapGestures { offset -> onSelect(indexAt(offset.x, size.width)) }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSelect(indexAt(change.position.x, size.width))
                }
            },
    ) {
        val topPad = 18.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val plotHeight = size.height - topPad - bottomPad
        val values = points.map { it.second }
        val minValue = minOf(0L, values.min())
        val maxValue = maxOf(values.max(), 0L)
        val span = (maxValue - minValue).takeIf { it > 0 } ?: 1L

        fun x(i: Int): Float = if (points.size == 1) size.width / 2 else i * size.width / (points.size - 1)
        fun y(value: Long): Float = topPad + plotHeight * (1f - (value - minValue).toFloat() / span)

        // Сетка: ноль, середина и максимум, подписи сверху над линиями.
        listOf(minValue, (minValue + maxValue) / 2, maxValue).distinct().forEach { level ->
            val lineY = y(level)
            drawLine(gridColor, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(MoneyFormat.compact(level), labelStyle)
            drawText(label, topLeft = Offset(0f, lineY - label.size.height - 2.dp.toPx()))
        }
        if (minValue < 0) {
            drawLine(crossColor, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.dp.toPx())
        }

        val line = Path()
        points.forEachIndexed { i, point ->
            if (i == 0) line.moveTo(x(i), y(point.second)) else line.lineTo(x(i), y(point.second))
        }
        if (points.size > 1) {
            val base = y(maxOf(minValue, 0L))
            val area = Path().apply {
                addPath(line)
                lineTo(x(points.lastIndex), base)
                lineTo(x(0), base)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(accent.primary.copy(alpha = 0.28f), Color.Transparent),
                    startY = topPad,
                    endY = topPad + plotHeight,
                ),
            )
            drawPath(line, accent.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Выбранный день: вертикальная линия и точка с кольцом цвета фона.
        val center = Offset(x(selected), y(points[selected].second))
        drawLine(crossColor, Offset(center.x, topPad), Offset(center.x, topPad + plotHeight), strokeWidth = 1.dp.toPx())
        drawCircle(accent.cardBottom, radius = 6.dp.toPx(), center = center)
        drawCircle(accent.light, radius = 4.dp.toPx(), center = center)

        val firstLabel = measurer.measure(shortDate(points.first().first), labelStyle)
        drawText(firstLabel, topLeft = Offset(0f, size.height - firstLabel.size.height))
        if (points.size > 1) {
            val lastLabel = measurer.measure(shortDate(points.last().first), labelStyle)
            drawText(lastLabel, topLeft = Offset(size.width - lastLabel.size.width, size.height - lastLabel.size.height))
        }
    }
}

/** Этот месяц: сколько пришло и ушло и на что уходит больше всего. */
@Composable
private fun MonthCard(entries: List<MoneyEntry>, today: LocalDate) {
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = monthStart.plusMonths(1).minusDays(1)
    val (income, expense) = MoneyStats.totals(entries, monthStart, monthEnd)
    val categories = MoneyStats.expensesByCategory(entries, monthStart, monthEnd)
    GlassCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = monthTitles[today.monthValue - 1], style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Пришло", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                Text(text = MoneyFormat.format(income, sign = true), style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Ушло", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                Text(text = MoneyFormat.format(-expense), style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            }
        }
        if (categories.isNotEmpty()) {
            GlassDivider()
            Text(text = "Куда уходят деньги", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
            val biggest = categories.first().second.coerceAtLeast(1L)
            categories.take(6).forEach { (category, sum) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = category,
                        modifier = Modifier.width(96.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((sum.toFloat() / biggest).coerceIn(0.03f, 1f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Palette.Violet),
                        )
                    }
                    Text(text = MoneyFormat.format(sum), style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: MoneyEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (entry.income) Palette.Violet.copy(alpha = 0.22f) else Palette.Chip)
                .border(1.dp, if (entry.income) Palette.Violet.copy(alpha = 0.5f) else Palette.BorderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (entry.income) "+" else "−",
                style = MaterialTheme.typography.titleMedium,
                color = if (entry.income) Palette.Lavender else Palette.TextMuted,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.category, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = MoneyFormat.format(entry.signed, sign = true),
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Text,
        )
    }
}
