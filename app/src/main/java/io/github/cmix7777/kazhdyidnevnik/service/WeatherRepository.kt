package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import io.github.cmix7777.kazhdyidnevnik.data.DayWeather
import io.github.cmix7777.kazhdyidnevnik.data.Forecast
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
        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,wind_gusts_10m,is_day" +
        "&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code," +
        "wind_speed_10m,wind_gusts_10m,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_min," +
        "precipitation_probability_max,precipitation_sum,snowfall_sum,wind_speed_10m_max,wind_gusts_10m_max" +
        "&wind_speed_unit=ms&timezone=Europe%2FSamara&forecast_days=8"

    private const val FRESH_MS = 2 * 60 * 60 * 1000L
    private const val STALE_FOR_SCREEN_MS = 45 * 60 * 1000L

    private fun file(context: Context) = File(context.filesDir, "weather.json")

    /** Сохранённый прогноз и когда он получен. */
    fun cached(context: Context): Pair<Long, Forecast>? {
        val file = file(context)
        if (!file.exists()) return null
        val forecast = runCatching { WeatherParser.parseForecast(file.readText()) }.getOrNull() ?: return null
        if (forecast.days.isEmpty()) return null
        return file.lastModified() to forecast
    }

    suspend fun fetch(context: Context, timeoutMs: Int = 15_000): Forecast = withContext(Dispatchers.IO) {
        val body = Jsoup.connect(URL)
            .ignoreContentType(true)
            .timeout(timeoutMs)
            .maxBodySize(0)
            .execute()
            .body()
        val forecast = WeatherParser.parseForecast(body)
        if (forecast.days.isNotEmpty()) file(context).writeText(body)
        forecast
    }

    /** Свежий прогноз из памяти, иначе с сайта, а без сети — какой есть в памяти. */
    suspend fun forecast(context: Context, timeoutMs: Int = 10_000): Forecast? {
        val cache = cached(context)
        if (cache != null && System.currentTimeMillis() - cache.first < FRESH_MS) return cache.second
        return runCatching { fetch(context, timeoutMs) }.getOrNull()?.takeIf { it.days.isNotEmpty() } ?: cache?.second
    }

    suspend fun forDate(context: Context, date: LocalDate, timeoutMs: Int = 10_000): DayWeather? =
        forecast(context, timeoutMs)?.day(date)

    /** Нужно ли обновить прогноз для экрана. */
    fun isStale(context: Context): Boolean {
        val cache = cached(context) ?: return true
        return System.currentTimeMillis() - cache.first > STALE_FOR_SCREEN_MS
    }
}
