package io.github.cmix7777.kazhdyidnevnik.data

import io.github.cmix7777.kazhdyidnevnik.formatTime
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** Когда выходить из дома и когда возвращаться — по парам (не онлайн) и сменам. */
data class Trip(val label: String, val time: LocalTime)

/** Погода в момент выхода или возвращения. */
data class TripWeather(val trip: Trip, val hour: HourWeather)

/** Текст уведомления о погоде. */
data class WeatherNote(val title: String, val text: String)

/** Превращает прогноз в пользу: погода в дорогу, что надеть, о чём предупредить. */
object WeatherAdvice {

    /** За сколько минут до первой пары выходит Айзат. У Насти своё время, см. [Profile]. */
    const val LEAVE_BEFORE_MIN = 40L
    private const val BACK_AFTER_MIN = 10L

    /**
     * Выход перед первым делом вне дома и возвращение после последнего. Онлайн-пары дорогу не требуют.
     * [leaveBefore] — за сколько минут до начала выходить.
     */
    fun trips(items: List<DayItem>, leaveBefore: Long = LEAVE_BEFORE_MIN): List<Trip> {
        val outside = items.filterNot { it is DayItem.LessonItem && it.lesson.online }
        if (outside.isEmpty()) return emptyList()
        return listOf(
            Trip("Выход", outside.minOf { it.start }.minusMinutes(leaveBefore)),
            Trip("Обратно", outside.maxOf { it.end }.plusMinutes(BACK_AFTER_MIN)),
        )
    }

    fun tripWeather(
        date: LocalDate,
        items: List<DayItem>,
        forecast: Forecast,
        leaveBefore: Long = LEAVE_BEFORE_MIN,
    ): List<TripWeather> =
        trips(items, leaveBefore).mapNotNull { trip ->
            forecast.hourAt(date.atTime(trip.time))?.let { TripWeather(trip, it) }
        }

    /** Что надеть, по ощущаемой температуре. */
    fun clothing(feels: Double): String = when {
        feels <= -25 -> "самая тёплая зимняя одежда, шапка, шарф, варежки, закрой лицо"
        feels <= -15 -> "зимняя куртка, шапка, шарф и перчатки"
        feels <= -5 -> "зимняя куртка, шапка и перчатки"
        feels <= 3 -> "тёплая куртка и шапка"
        feels <= 10 -> "куртка потеплее"
        feels <= 16 -> "лёгкая куртка или ветровка"
        feels <= 22 -> "кофта или лёгкая куртка"
        else -> "можно в футболке"
    }

    /** Что ещё взять или учесть в эти часы: зонт, капюшон, гололёд, ветер. */
    fun gear(hours: List<HourWeather>, day: DayWeather?): List<String> {
        val result = mutableListOf<String>()
        val wet = hours.any { (it.precipitationChance ?: 0) >= 50 || (it.precipitation ?: 0.0) >= 0.3 }
        val snowy = hours.any { it.code in WeatherText.SNOW }
        val rainy = hours.any { it.code in WeatherText.RAIN || it.code in WeatherText.FREEZING }
        if (wet && rainy) result += "возьми зонт"
        if (wet && snowy) result += "нужен капюшон"
        val nearZero = hours.any { it.temperature in -4.0..2.0 }
        val freezing = hours.any { it.code in WeatherText.FREEZING } ||
            (day != null && day.code in WeatherText.FREEZING)
        if (freezing || (nearZero && (day?.precipitationSum ?: 0.0) >= 0.5)) result += "скользко, иди осторожно"
        val gusts = hours.mapNotNull { it.gusts }.maxOrNull()
        if (gusts != null && gusts >= 14) result += "ветер с порывами до ${gusts.roundToInt()} м/с"
        return result
    }

    /** Совет на дорогу: одежда по самому холодному моменту плюс снаряжение. */
    fun advice(trips: List<TripWeather>, day: DayWeather?): String? {
        if (trips.isEmpty()) return null
        val coldest = trips.minOf { it.hour.feels }
        val parts = listOf(clothing(coldest)) + gear(trips.map { it.hour }, day)
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }

    /** «Выход в 7:20: −5°, ощущается −11°, снег». */
    fun tripLine(trip: TripWeather): String {
        val hour = trip.hour
        val feels = hour.feelsLike?.takeIf { (it - hour.temperature).let { d -> d <= -2 || d >= 2 } }
            ?.let { ", ощущается ${WeatherText.temperature(it)}" }
            .orEmpty()
        val rain = hour.precipitationChance?.takeIf { it >= 30 }?.let { ", осадки $it%" }.orEmpty()
        return "${trip.trip.label} в ${formatTime(trip.trip.time)}: ${WeatherText.temperature(hour.temperature)}$feels, " +
            WeatherText.condition(hour.code) + rain
    }

    /** Предупреждения на день. [previous] — предыдущий день, чтобы заметить резкое похолодание. */
    fun alerts(day: DayWeather, previous: DayWeather? = null): List<String> {
        val result = mutableListOf<String>()
        val snow = day.snowfall ?: 0.0
        val sum = day.precipitationSum ?: 0.0
        if (day.code in 95..99) result += "гроза"
        if (day.code in setOf(65, 82) || (sum >= 10 && snow < 1)) result += "сильный дождь"
        if (snow >= 3 || day.code in setOf(75, 86)) result += "сильный снег" else if (snow >= 0.5) result += "снег"
        if (day.code in WeatherText.FREEZING || (day.min <= 0 && day.max >= -3 && sum >= 0.5)) result += "гололёд"
        val gusts = day.gustsMax ?: 0.0
        if (gusts >= 15) result += "ветер до ${gusts.roundToInt()} м/с"
        val coldest = day.feelsMin ?: day.min
        if (coldest <= -25) result += "сильный мороз"
        if (previous != null && previous.max - day.max >= 8) result += "резкое похолодание"
        return result
    }

    /** Строки погоды для утренней сводки. */
    fun morningLines(
        date: LocalDate,
        items: List<DayItem>?,
        forecast: Forecast?,
        leaveBefore: Long = LEAVE_BEFORE_MIN,
    ): List<String> {
        val day = forecast?.day(date) ?: return emptyList()
        val lines = mutableListOf("Погода: ${WeatherText.short(day)}.")
        val trips = if (items != null) tripWeather(date, items, forecast, leaveBefore) else emptyList()
        trips.forEach { lines += tripLine(it) + "." }
        advice(trips, day)?.let { lines += it }
        val alerts = alerts(day, forecast.day(date.minusDays(1)))
        if (alerts.isNotEmpty()) lines += "Внимание: ${alerts.joinToString(", ")}."
        return lines
    }

    /** Уведомление вечером о погоде на [date] (на завтра). null — прогноза нет. */
    fun tomorrowNote(
        date: LocalDate,
        items: List<DayItem>?,
        forecast: Forecast?,
        leaveBefore: Long = LEAVE_BEFORE_MIN,
    ): WeatherNote? {
        val day = forecast?.day(date) ?: return null
        val alerts = alerts(day, forecast.day(date.minusDays(1)))
        val title = if (alerts.isEmpty()) {
            "Погода на завтра: ${WeatherText.temperature(day.min)}…${WeatherText.temperature(day.max)}"
        } else {
            "Завтра: " + alerts.joinToString(", ")
        }
        val lines = mutableListOf(
            "${WeatherText.short(day).replaceFirstChar { it.uppercase() }}" +
                (day.precipitationChance?.takeIf { it >= 30 }?.let { ", осадки $it%" }.orEmpty()) + ".",
        )
        val trips = if (items != null) tripWeather(date, items, forecast, leaveBefore) else emptyList()
        trips.forEach { lines += tripLine(it) + "." }
        if (trips.isEmpty()) {
            lines += "Выходить никуда не нужно."
        } else {
            advice(trips, day)?.let { lines += it }
        }
        return WeatherNote(title, lines.joinToString("\n"))
    }
}
