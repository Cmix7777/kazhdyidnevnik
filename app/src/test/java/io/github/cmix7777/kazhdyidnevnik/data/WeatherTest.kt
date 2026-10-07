package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeatherTest {

    private val days = WeatherParser.parse(
        requireNotNull(javaClass.getResource("/weather/izhevsk.json")).readText(),
    )
    private val thursday = days.first { it.date == LocalDate.of(2026, 10, 8) }
    private val friday = days.first { it.date == LocalDate.of(2026, 10, 9) }

    @Test
    fun parsesDailyAndMorningValues() {
        assertEquals(2, days.size)
        assertEquals(1.8, thursday.morning!!, 0.001)
        assertEquals(-1.4, thursday.min, 0.001)
        assertEquals(4.6, thursday.max, 0.001)
        assertEquals(3, thursday.code)
        assertEquals(70, friday.precipitationChance)
    }

    @Test
    fun describesWeatherInRussian() {
        assertEquals("утром +2°, днём до +5°, пасмурно, ветер до 5 м/с", WeatherText.summary(thursday))
        assertEquals("−1°…+5°, пасмурно", WeatherText.short(thursday))
        assertEquals("утром +4°, днём до +7°, небольшой дождь, ветер до 9 м/с", WeatherText.summary(friday))
    }

    @Test
    fun umbrellaOnlyWhenRainIsLikely() {
        assertNull(WeatherText.advice(thursday))
        assertEquals("Возможны осадки (70%), возьми зонт.", WeatherText.advice(friday))
        assertEquals("Возможен снег (60%).", WeatherText.advice(friday.copy(code = 73, precipitationChance = 60)))
    }

    @Test
    fun temperatureSigns() {
        assertEquals("0°", WeatherText.temperature(0.3))
        assertEquals("−12°", WeatherText.temperature(-11.6))
        assertEquals("+21°", WeatherText.temperature(20.5))
    }

    @Test
    fun brokenAnswerGivesNothing() {
        assertTrue(WeatherParser.parse("""{"error":true,"reason":"bad"}""").isEmpty())
    }

    @Test
    fun morningSummaryIncludesWeather() {
        val text = Summaries.morning(friday.date, emptyList(), emptyList(), friday)
        assertTrue(text, text.contains("Погода: утром +4°, днём до +7°, небольшой дождь, ветер до 9 м/с."))
        assertTrue(text, text.contains("возьми зонт"))
    }
}
