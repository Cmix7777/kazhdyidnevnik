package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import io.github.cmix7777.kazhdyidnevnik.data.DayWeather
import io.github.cmix7777.kazhdyidnevnik.data.WeatherParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.time.LocalDate

/** Погода в Ижевске с open-meteo.com (бесплатно, без ключа). Последний ответ хранится на телефоне. */
object WeatherRepository {

    private const val URL = "https://api.open-meteo.com/v1/forecast" +
        "?latitude=56.85&longitude=53.20" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max" +
        "&hourly=temperature_2m&wind_speed_unit=ms&timezone=Europe%2FSamara&forecast_days=3"

    private const val FRESH_MS = 3 * 60 * 60 * 1000L

    private fun file(context: Context) = File(context.filesDir, "weather.json")

    /** Сохранённый прогноз и когда он получен. */
    fun cached(context: Context): Pair<Long, List<DayWeather>>? {
        val file = file(context)
        if (!file.exists()) return null
        val days = runCatching { WeatherParser.parse(file.readText()) }.getOrNull() ?: return null
        return file.lastModified() to days
    }

    suspend fun fetch(context: Context, timeoutMs: Int = 15_000): List<DayWeather> = withContext(Dispatchers.IO) {
        val body = Jsoup.connect(URL)
            .ignoreContentType(true)
            .timeout(timeoutMs)
            .execute()
            .body()
        val days = WeatherParser.parse(body)
        if (days.isNotEmpty()) file(context).writeText(body)
        days
    }

    /** Погода на день: свежая из памяти, иначе с сайта, а без сети — что есть в памяти. */
    suspend fun forDate(context: Context, date: LocalDate, timeoutMs: Int = 10_000): DayWeather? {
        val cache = cached(context)
        val fresh = cache != null && System.currentTimeMillis() - cache.first < FRESH_MS
        cache?.second?.firstOrNull { it.date == date }?.let { if (fresh) return it }
        return runCatching { fetch(context, timeoutMs) }.getOrNull()
            ?.firstOrNull { it.date == date }
            ?: cache?.second?.firstOrNull { it.date == date }
    }

    /** Нужно ли обновить прогноз для экрана (старше часа). */
    fun isStale(context: Context): Boolean {
        val cache = cached(context) ?: return true
        return System.currentTimeMillis() - cache.first > 60 * 60 * 1000L
    }
}
