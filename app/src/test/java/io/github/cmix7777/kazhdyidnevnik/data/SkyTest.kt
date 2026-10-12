package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

class SkyTest {

    private fun minutes(hours: Double) = hours * 60.0

    private fun assertTime(expectedHour: Int, expectedMinute: Int, actualHours: Double, toleranceMinutes: Double = 4.0) {
        val expected = expectedHour * 60.0 + expectedMinute
        val actual = minutes(actualHours)
        assertTrue("ожидалось около $expectedHour:$expectedMinute, получилось $actualHours", abs(expected - actual) <= toleranceMinutes)
    }

    @Test
    fun sunriseAndSunsetInIzhevsk() {
        // Справочные значения для Ижевска (UTC+4), с точностью до пары минут.
        val october = Astronomy.sun(LocalDate.of(2026, 10, 12))!!
        assertTime(6, 52, october.first)
        assertTime(17, 34, october.second)
        val summer = Astronomy.sun(LocalDate.of(2026, 6, 21))!!
        assertTime(3, 33, summer.first, 6.0)
        assertTime(21, 24, summer.second, 6.0)
        val winter = Astronomy.sun(LocalDate.of(2026, 12, 21))!!
        assertTime(9, 3, winter.first, 6.0)
        assertTime(15, 48, winter.second, 6.0)
    }

    @Test
    fun moonPhase() {
        // Новолуние 10 октября 2026, полнолуние 26 октября 2026.
        val nearNew = Astronomy.moonPhaseLocal(LocalDateTime.of(2026, 10, 12, 12, 0))
        assertTrue("$nearNew", nearNew < 0.1)
        val nearFull = Astronomy.moonPhaseLocal(LocalDateTime.of(2026, 10, 26, 12, 0))
        assertTrue("$nearFull", abs(nearFull - 0.5) < 0.04)
        val phase = Astronomy.moonPhase(LocalDateTime.of(1999, 1, 1, 0, 0))
        assertTrue(phase in 0.0..1.0)
    }

    @Test
    fun skyTakesCloudsFromForecast() {
        val now = LocalDateTime.of(2026, 10, 12, 21, 30)
        val forecast = Forecast(
            current = CurrentWeather(time = now, temperature = 3.0, feelsLike = 0.0, code = 3, wind = 2.0, gusts = 4.0, isDay = false, cloudCover = 85),
            hours = emptyList(),
            days = emptyList(),
        )
        val sky = SkyState.at(now, forecast)
        assertEquals(0.85, sky.clouds, 0.001)
        assertEquals(21.5, sky.hour, 0.001)
        assertTrue(sky.sunrise < sky.sunset)
        assertEquals(0.3, SkyState.at(now, null).clouds, 0.001)
    }

    private fun weather(code: Int, temperature: Double): Forecast {
        val now = LocalDateTime.of(2026, 10, 12, 12, 0)
        return Forecast(
            current = CurrentWeather(time = now, temperature = temperature, feelsLike = temperature, code = code, wind = 1.0, gusts = 2.0, isDay = true),
            hours = emptyList(),
            days = emptyList(),
        )
    }

    @Test
    fun glassFollowsWeather() {
        val now = LocalDateTime.of(2026, 10, 12, 12, 0)
        assertEquals(GlassMode.RAIN, GlassWeather.from(weather(61, 5.0), now).mode)
        assertEquals(1f, GlassWeather.from(weather(65, 5.0), now).strength, 0.001f)
        assertTrue(GlassWeather.from(weather(53, 5.0), now).strength < GlassWeather.from(weather(63, 5.0), now).strength)
        assertEquals(GlassMode.SNOW, GlassWeather.from(weather(73, -3.0), now).mode)
        assertEquals(GlassMode.FROST, GlassWeather.from(weather(0, -15.0), now).mode)
        assertEquals(GlassMode.NONE, GlassWeather.from(weather(3, -5.0), now).mode)
        assertEquals(GlassMode.NONE, GlassWeather.from(weather(0, 18.0), now).mode)
        assertEquals(GlassWeather.NONE, GlassWeather.from(null, now))
    }

    @Test
    fun parsesCloudCover() {
        val json = """
            {"current":{"time":"2026-10-12T21:30","temperature_2m":3.1,"weather_code":3,"cloud_cover":78},
             "hourly":{"time":["2026-10-12T21:00","2026-10-12T22:00"],"temperature_2m":[3.0,2.6],"cloud_cover":[70,100]}}
        """.trimIndent()
        val forecast = WeatherParser.parseForecast(json)
        assertEquals(78, forecast.current!!.cloudCover)
        assertEquals(listOf(70, 100), forecast.hours.map { it.cloudCover })
    }
}
