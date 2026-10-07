package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

/** Погода на день в Ижевске. [morning] — температура в 8 утра, когда выходить из дома. */
data class DayWeather(
    val date: LocalDate,
    val min: Double,
    val max: Double,
    val morning: Double?,
    val code: Int,
    val precipitationChance: Int?,
    val windMax: Double?,
)

/** Разбирает ответ open-meteo.com. */
object WeatherParser {

    private val morningTime: LocalTime = LocalTime.of(8, 0)

    fun parse(json: String): List<DayWeather> {
        val root = Json.parseToJsonElement(json).jsonObject
        val daily = root["daily"]?.jsonObject ?: return emptyList()
        val dates = daily["time"]?.jsonArray?.map { LocalDate.parse(it.jsonPrimitive.content) } ?: return emptyList()
        val max = doubles(daily, "temperature_2m_max")
        val min = doubles(daily, "temperature_2m_min")
        val codes = daily["weather_code"]?.jsonArray?.map { it.jsonPrimitive.intOrNull }
        val rain = daily["precipitation_probability_max"]?.jsonArray?.map { it.jsonPrimitive.intOrNull }
        val wind = doubles(daily, "wind_speed_10m_max")

        val hourly = root["hourly"]?.jsonObject
        val hourTimes = hourly?.get("time")?.jsonArray?.map { LocalDateTime.parse(it.jsonPrimitive.content) }
        val hourTemps = hourly?.let { doubles(it, "temperature_2m") }
        val hourTemp = buildMap<LocalDateTime, Double> {
            if (hourTimes != null && hourTemps != null) {
                hourTimes.zip(hourTemps).forEach { (time, temp) -> if (temp != null) put(time, temp) }
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
            )
        }
    }

    private fun doubles(obj: JsonObject, name: String): List<Double?>? =
        obj[name]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull }
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

    /** Коротко для экрана «Сегодня»: «+2…+8°, пасмурно». */
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

    private val SNOW = setOf(71, 73, 75, 77, 85, 86)
}
