package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

/** Погода прямо сейчас. */
data class CurrentWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val feelsLike: Double?,
    val code: Int,
    val wind: Double?,
    val gusts: Double?,
    val isDay: Boolean,
)

/** Погода на один час. */
data class HourWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val feelsLike: Double?,
    val precipitationChance: Int?,
    val precipitation: Double?,
    val code: Int,
    val wind: Double?,
    val gusts: Double?,
    val isDay: Boolean,
) {
    val feels: Double get() = feelsLike ?: temperature
}

/** Погода на день. [morning] — температура в 8 утра. Осадки в мм, снег в см, ветер в м/с. */
data class DayWeather(
    val date: LocalDate,
    val min: Double,
    val max: Double,
    val morning: Double?,
    val code: Int,
    val precipitationChance: Int?,
    val windMax: Double?,
    val feelsMin: Double? = null,
    val precipitationSum: Double? = null,
    val snowfall: Double? = null,
    val gustsMax: Double? = null,
)

/** Весь прогноз: сейчас, по часам и по дням. */
data class Forecast(
    val current: CurrentWeather?,
    val hours: List<HourWeather>,
    val days: List<DayWeather>,
) {
    fun day(date: LocalDate): DayWeather? = days.firstOrNull { it.date == date }

    /** Ближайший к [time] час прогноза (в пределах полутора часов). */
    fun hourAt(time: LocalDateTime): HourWeather? = hours
        .minByOrNull { kotlin.math.abs(java.time.Duration.between(it.time, time).toMinutes()) }
        ?.takeIf { kotlin.math.abs(java.time.Duration.between(it.time, time).toMinutes()) <= 90 }

    /** Часы начиная с [from] (текущий час включительно). */
    fun hoursFrom(from: LocalDateTime, count: Int): List<HourWeather> {
        val start = from.withMinute(0).withSecond(0).withNano(0)
        return hours.filter { !it.time.isBefore(start) }.take(count)
    }
}

/** Разбирает ответ open-meteo.com. */
object WeatherParser {

    private val morningTime: LocalTime = LocalTime.of(8, 0)

    fun parse(json: String): List<DayWeather> = parseForecast(json).days

    fun parseForecast(json: String): Forecast {
        val root = Json.parseToJsonElement(json).jsonObject
        return Forecast(
            current = root["current"]?.jsonObject?.let { current(it) },
            hours = root["hourly"]?.jsonObject?.let { hours(it) }.orEmpty(),
            days = root["daily"]?.jsonObject?.let { daily(it, root["hourly"]?.jsonObject) }.orEmpty(),
        )
    }

    private fun current(obj: JsonObject): CurrentWeather? {
        val time = obj["time"]?.jsonPrimitive?.contentOrNull?.let { LocalDateTime.parse(it) } ?: return null
        val temperature = obj.double("temperature_2m") ?: return null
        return CurrentWeather(
            time = time,
            temperature = temperature,
            feelsLike = obj.double("apparent_temperature"),
            code = obj.int("weather_code") ?: 0,
            wind = obj.double("wind_speed_10m"),
            gusts = obj.double("wind_gusts_10m"),
            isDay = (obj.int("is_day") ?: 1) == 1,
        )
    }

    private fun hours(obj: JsonObject): List<HourWeather> {
        val times = obj["time"]?.jsonArray?.map { LocalDateTime.parse(it.jsonPrimitive.content) } ?: return emptyList()
        val temps = obj.doubles("temperature_2m") ?: return emptyList()
        val feels = obj.doubles("apparent_temperature")
        val chance = obj.ints("precipitation_probability")
        val precipitation = obj.doubles("precipitation")
        val codes = obj.ints("weather_code")
        val wind = obj.doubles("wind_speed_10m")
        val gusts = obj.doubles("wind_gusts_10m")
        val isDay = obj.ints("is_day")
        return times.mapIndexedNotNull { i, time ->
            val temperature = temps.getOrNull(i) ?: return@mapIndexedNotNull null
            HourWeather(
                time = time,
                temperature = temperature,
                feelsLike = feels?.getOrNull(i),
                precipitationChance = chance?.getOrNull(i),
                precipitation = precipitation?.getOrNull(i),
                code = codes?.getOrNull(i) ?: 0,
                wind = wind?.getOrNull(i),
                gusts = gusts?.getOrNull(i),
                isDay = (isDay?.getOrNull(i) ?: if (time.hour in 7..18) 1 else 0) == 1,
            )
        }
    }

    private fun daily(obj: JsonObject, hourly: JsonObject?): List<DayWeather> {
        val dates = obj["time"]?.jsonArray?.map { LocalDate.parse(it.jsonPrimitive.content) } ?: return emptyList()
        val max = obj.doubles("temperature_2m_max")
        val min = obj.doubles("temperature_2m_min")
        val codes = obj.ints("weather_code")
        val rain = obj.ints("precipitation_probability_max")
        val wind = obj.doubles("wind_speed_10m_max")
        val feelsMin = obj.doubles("apparent_temperature_min")
        val sum = obj.doubles("precipitation_sum")
        val snow = obj.doubles("snowfall_sum")
        val gusts = obj.doubles("wind_gusts_10m_max")

        val hourTemp = buildMap<LocalDateTime, Double> {
            val times = hourly?.get("time")?.jsonArray?.map { LocalDateTime.parse(it.jsonPrimitive.content) }
            val temps = hourly?.doubles("temperature_2m")
            if (times != null && temps != null) {
                times.zip(temps).forEach { (time, temp) -> if (temp != null) put(time, temp) }
            }
        }

        return dates.mapIndexedNotNull { i, date ->
            val dayMax = max?.getOrNull(i) ?: return@mapIndexedNotNull null
            val dayMin = min?.getOrNull(i) ?: return@mapIndexedNotNull null
            DayWeather(
                date = date,
                min = dayMin,
                max = dayMax,
                morning = hourTemp[date.atTime(morningTime)],
                code = codes?.getOrNull(i) ?: 0,
                precipitationChance = rain?.getOrNull(i),
                windMax = wind?.getOrNull(i),
                feelsMin = feelsMin?.getOrNull(i),
                precipitationSum = sum?.getOrNull(i),
                snowfall = snow?.getOrNull(i),
                gustsMax = gusts?.getOrNull(i),
            )
        }
    }

    private fun JsonObject.double(name: String): Double? = this[name]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.doubleOrNull?.roundToInt()

    private fun JsonObject.doubles(name: String): List<Double?>? =
        this[name]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull }

    private fun JsonObject.ints(name: String): List<Int?>? =
        this[name]?.jsonArray?.map { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.doubleOrNull?.roundToInt() }
}

/** Погода словами. */
object WeatherText {

    /** «утром +2°, днём до +8°, пасмурно, ветер до 7 м/с». */
    fun summary(day: DayWeather): String {
        val parts = mutableListOf<String>()
        day.morning?.let { parts += "утром ${temperature(it)}" }
        parts += "днём до ${temperature(day.max)}"
        parts += condition(day.code)
        day.windMax?.let { parts += "ветер до ${it.roundToInt()} м/с" }
        return parts.joinToString(", ")
    }

    /** Совет, если может пойти дождь или снег. */
    fun advice(day: DayWeather): String? {
        val chance = day.precipitationChance ?: return null
        if (chance < 50) return null
        return if (day.code in SNOW) {
            "Возможен снег ($chance%)."
        } else {
            "Возможны осадки ($chance%), возьми зонт."
        }
    }

    /** Коротко: «−1°…+5°, пасмурно». */
    fun short(day: DayWeather): String =
        "${temperature(day.min)}…${temperature(day.max)}, ${condition(day.code)}"

    fun temperature(value: Double): String {
        val rounded = value.roundToInt()
        return when {
            rounded > 0 -> "+$rounded°"
            rounded < 0 -> "−${-rounded}°"
            else -> "0°"
        }
    }

    /** Коды погоды WMO, которые отдаёт open-meteo. */
    fun condition(code: Int): String = when (code) {
        0 -> "ясно"
        1 -> "в основном ясно"
        2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        51, 53, 55 -> "морось"
        56, 57 -> "ледяная морось"
        61 -> "небольшой дождь"
        63 -> "дождь"
        65 -> "сильный дождь"
        66, 67 -> "ледяной дождь"
        71 -> "небольшой снег"
        73 -> "снег"
        75 -> "сильный снег"
        77 -> "снежная крупа"
        80, 81 -> "ливень"
        82 -> "сильный ливень"
        85, 86 -> "снегопад"
        95 -> "гроза"
        96, 99 -> "гроза с градом"
        else -> "облачно"
    }

    val SNOW = setOf(71, 73, 75, 77, 85, 86)
    val RAIN = setOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99)
    val FREEZING = setOf(56, 57, 66, 67)
}
