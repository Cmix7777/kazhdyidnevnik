package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.DayItem
import io.github.cmix7777.kazhdyidnevnik.data.DayWeather
import io.github.cmix7777.kazhdyidnevnik.data.Forecast
import io.github.cmix7777.kazhdyidnevnik.data.HourWeather
import io.github.cmix7777.kazhdyidnevnik.data.WeatherAdvice
import io.github.cmix7777.kazhdyidnevnik.data.WeatherText
import io.github.cmix7777.kazhdyidnevnik.formatStamp
import io.github.cmix7777.kazhdyidnevnik.formatTime
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val SunColor = Color(0xFFFFC857)
private val CloudColor = Color(0xFFE6E0F2)
private val MoonColor = Color(0xFFD9CCFF)
private val RainCloudColor = Color(0xFFB4AACB)
private val RainColor = Color(0xFF7DD3FC)

/** Карточка погоды на «Сегодня»: сейчас, по часам, погода в дорогу и что надеть. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherCard(
    forecast: Forecast?,
    updatedMillis: Long,
    error: String?,
    loading: Boolean,
    date: LocalDate,
    items: List<DayItem>,
    now: LocalDateTime,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    leaveBefore: Long = WeatherAdvice.LEAVE_BEFORE_MIN,
) {
    val day = forecast?.day(date)
    if (forecast == null || day == null) {
        GlassCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Погода", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(
                text = when {
                    loading -> "Загружаю прогноз…"
                    error != null -> "Не получилось загрузить прогноз: $error."
                    else -> "Прогноза пока нет."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            if (!loading) GhostButton(text = "Обновить погоду", onClick = onRetry, small = true)
        }
        return
    }

    val current = forecast.current?.takeIf { it.time.toLocalDate() == date }
    val nowHour = forecast.hourAt(now)
    val temperature = current?.temperature ?: nowHour?.temperature ?: day.max
    val feels = current?.feelsLike ?: nowHour?.feelsLike
    val code = current?.code ?: nowHour?.code ?: day.code
    val isDay = current?.isDay ?: nowHour?.isDay ?: true
    val wind = current?.wind ?: nowHour?.wind
    val gusts = current?.gusts ?: nowHour?.gusts
    val trips = WeatherAdvice.tripWeather(date, items, forecast, leaveBefore)
    val advice = WeatherAdvice.advice(trips, day)
    val alerts = WeatherAdvice.alerts(day, forecast.day(date.minusDays(1)))

    GlassCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Сейчас.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WeatherIcon(code = code, isDay = isDay, modifier = Modifier.size(56.dp))
            Text(
                text = WeatherText.temperature(temperature),
                style = MaterialTheme.typography.displaySmall,
                color = Palette.Text,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = WeatherText.condition(code).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Text,
                )
                feels?.let {
                    Text(
                        text = "ощущается ${WeatherText.temperature(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                    )
                }
                wind?.let {
                    val gust = gusts?.takeIf { g -> g - it >= 3 }?.let { g -> ", порывы ${g.roundToInt()}" }.orEmpty()
                    Text(
                        text = "ветер ${it.roundToInt()} м/с$gust",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                    )
                }
            }
        }
        Text(
            text = "Днём ${WeatherText.short(day)}" +
                (day.precipitationChance?.takeIf { it >= 20 }?.let { ", осадки $it%" }.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
        )

        if (alerts.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                alerts.forEach { AlertPill(it) }
            }
        }

        // По часам.
        val hours = forecast.hoursFrom(now, 12)
        if (hours.isNotEmpty()) {
            HourlyStrip(hours)
        }

        // В дорогу.
        if (trips.isNotEmpty()) {
            GlassDivider()
            Text("В дорогу", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            trips.forEach { trip ->
                val passed = now.toLocalTime().isAfter(trip.trip.time.plusMinutes(30))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeatherIcon(code = trip.hour.code, isDay = trip.hour.isDay, modifier = Modifier.size(26.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${trip.trip.label} в ${formatTime(trip.trip.time)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (passed) Palette.TextFaint else Palette.Lavender,
                        )
                        Text(
                            text = tripDetails(trip.hour),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (passed) Palette.TextFaint else Palette.TextMuted,
                        )
                    }
                    Text(
                        text = WeatherText.temperature(trip.hour.temperature),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (passed) Palette.TextFaint else Palette.Text,
                    )
                }
            }
            advice?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Violet.copy(alpha = 0.14f))
                        .border(1.dp, Palette.Violet.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Text,
                )
            }
        } else if (items.isNotEmpty()) {
            Text(
                text = "Сегодня всё онлайн, выходить не нужно.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }

        Text(
            text = buildString {
                append(if (updatedMillis > 0) "обновлено ${formatStamp(updatedMillis)}" else "")
                if (error != null) append(" · не обновилось: $error")
                append(" · open-meteo.com")
            }.trimStart(' ', '·'),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextFaint,
        )
    }
}

private fun tripDetails(hour: HourWeather): String {
    val parts = mutableListOf(WeatherText.condition(hour.code))
    hour.feelsLike?.takeIf { kotlin.math.abs(it - hour.temperature) >= 2 }
        ?.let { parts += "ощущается ${WeatherText.temperature(it)}" }
    hour.precipitationChance?.takeIf { it >= 30 }?.let { parts += "осадки $it%" }
    hour.gusts?.takeIf { it >= 10 }?.let { parts += "порывы ${it.roundToInt()} м/с" }
    return parts.joinToString(", ")
}

/** Почасовой прогноз: время, значок, температура, вероятность осадков. */
@Composable
private fun HourlyStrip(hours: List<HourWeather>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hours.forEachIndexed { index, hour ->
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (index == 0) Palette.Violet.copy(alpha = 0.18f) else Palette.Chip)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (index == 0) "сейчас" else "%d:00".format(hour.time.hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == 0) Palette.Lavender else Palette.TextMuted,
                    textAlign = TextAlign.Center,
                )
                WeatherIcon(code = hour.code, isDay = hour.isDay, modifier = Modifier.size(24.dp))
                Text(
                    text = WeatherText.temperature(hour.temperature),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Text,
                )
                val chance = hour.precipitationChance ?: 0
                Text(
                    text = if (chance >= 10) "$chance%" else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = RainColor,
                )
            }
        }
    }
}

/** Таблетка-предупреждение: «гололёд», «сильный ветер». */
@Composable
fun AlertPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Palette.Danger.copy(alpha = 0.14f))
            .border(1.dp, Palette.Danger.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFB3C0))
    }
}

/** Маленькая таблетка с прогнозом на день: значок, температура, осадки. */
@Composable
fun WeatherPill(day: DayWeather, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Palette.Chip)
            .border(1.dp, Palette.Border, CircleShape)
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WeatherIcon(code = day.code, isDay = true, modifier = Modifier.size(18.dp))
        val rain = day.precipitationChance?.takeIf { it >= 30 }?.let { " · $it%" }.orEmpty()
        Text(
            text = "${WeatherText.temperature(day.min)}…${WeatherText.temperature(day.max)}, ${WeatherText.condition(day.code)}$rain",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextMuted,
        )
    }
}

/** Значок погоды, нарисованный кодом (без картинок): солнце, луна, облака, дождь, снег, гроза, туман. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (code) {
            0 -> if (isDay) drawSun(Offset(w * 0.5f, h * 0.5f), w * 0.2f) else drawMoon(Offset(w * 0.5f, h * 0.5f), w * 0.3f)
            1, 2 -> {
                if (isDay) drawSun(Offset(w * 0.36f, h * 0.36f), w * 0.15f) else drawMoon(Offset(w * 0.38f, h * 0.36f), w * 0.22f)
                drawCloud(w * 0.2f, h * 0.38f, w * 0.72f, h * 0.44f, CloudColor)
            }
            3 -> drawCloud(w * 0.1f, h * 0.24f, w * 0.8f, h * 0.5f, CloudColor)
            45, 48 -> {
                drawCloud(w * 0.12f, h * 0.12f, w * 0.76f, h * 0.42f, CloudColor.copy(alpha = 0.85f))
                for (i in 0..1) {
                    val y = h * (0.66f + i * 0.16f)
                    drawLine(CloudColor, Offset(w * 0.16f, y), Offset(w * 0.84f, y), strokeWidth = h * 0.07f, cap = StrokeCap.Round)
                }
            }
            in 71..77, 85, 86 -> {
                drawCloud(w * 0.1f, h * 0.08f, w * 0.8f, h * 0.48f, CloudColor)
                listOf(0.3f, 0.5f, 0.7f).forEachIndexed { i, x ->
                    drawCircle(Color.White, radius = w * 0.055f, center = Offset(w * x, h * (0.72f + (i % 2) * 0.12f)))
                }
            }
            in 95..99 -> {
                drawCloud(w * 0.1f, h * 0.08f, w * 0.8f, h * 0.48f, RainCloudColor)
                val bolt = Path().apply {
                    moveTo(w * 0.52f, h * 0.5f)
                    lineTo(w * 0.38f, h * 0.74f)
                    lineTo(w * 0.5f, h * 0.74f)
                    lineTo(w * 0.42f, h * 0.95f)
                    lineTo(w * 0.64f, h * 0.66f)
                    lineTo(w * 0.52f, h * 0.66f)
                    lineTo(w * 0.6f, h * 0.5f)
                    close()
                }
                drawPath(bolt, SunColor)
            }
            in 51..67, in 80..82 -> {
                drawCloud(w * 0.1f, h * 0.08f, w * 0.8f, h * 0.48f, RainCloudColor)
                listOf(0.32f, 0.52f, 0.72f).forEach { x ->
                    drawLine(
                        RainColor,
                        Offset(w * x, h * 0.68f),
                        Offset(w * (x - 0.08f), h * 0.9f),
                        strokeWidth = w * 0.07f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            else -> drawCloud(w * 0.1f, h * 0.24f, w * 0.8f, h * 0.5f, CloudColor)
        }
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float) {
    drawCircle(SunColor, radius = radius, center = center)
    for (i in 0 until 8) {
        val angle = Math.toRadians(i * 45.0)
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            SunColor,
            Offset(center.x + dx * radius * 1.45f, center.y + dy * radius * 1.45f),
            Offset(center.x + dx * radius * 1.95f, center.y + dy * radius * 1.95f),
            strokeWidth = radius * 0.3f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMoon(center: Offset, radius: Float) {
    val outer = Path().apply { addOval(Rect(center, radius)) }
    val inner = Path().apply { addOval(Rect(Offset(center.x + radius * 0.5f, center.y - radius * 0.35f), radius * 0.85f)) }
    val crescent = Path().apply { op(outer, inner, PathOperation.Difference) }
    drawPath(crescent, MoonColor)
}

private fun DrawScope.drawCloud(left: Float, top: Float, width: Float, height: Float, color: Color) {
    val cloud = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(left, top + height * 0.42f, left + width, top + height),
                cornerRadius = CornerRadius(height * 0.29f),
            ),
        )
        addOval(Rect(Offset(left + width * 0.34f, top + height * 0.5f), height * 0.36f))
        addOval(Rect(Offset(left + width * 0.62f, top + height * 0.4f), height * 0.42f))
    }
    drawPath(cloud, color)
}
