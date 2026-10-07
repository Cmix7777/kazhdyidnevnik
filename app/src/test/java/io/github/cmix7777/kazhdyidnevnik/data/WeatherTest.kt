package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WeatherTest {

    private val forecast = WeatherParser.parseForecast(
        requireNotNull(javaClass.getResource("/weather/izhevsk.json")).readText(),
    )
    private val lessons = TimeoParser.parse(
        requireNotNull(javaClass.getResource("/timeo/week-2026-10-05.html")).readText(),
        LocalDate.of(2026, 10, 5),
    )
    private val thursdayDate = LocalDate.of(2026, 10, 8)
    private val fridayDate = LocalDate.of(2026, 10, 9)
    private val thursday = forecast.day(thursdayDate)!!
    private val friday = forecast.day(fridayDate)!!

    private fun items(date: LocalDate) = buildDay(date, lessons, WorkSchedule.default)

    @Test
    fun parsesCurrentHoursAndDays() {
        assertEquals(3, forecast.days.size)
        assertEquals(72, forecast.hours.size)
        assertEquals(4.2, forecast.current!!.temperature, 0.001)
        assertEquals(0.5, forecast.current!!.feelsLike!!, 0.001)
        assertEquals(1.8, thursday.morning!!, 0.001)
        assertEquals(3.5, friday.snowfall!!, 0.001)
        assertEquals(16.0, friday.gustsMax!!, 0.001)
    }

    @Test
    fun nearestHour() {
        val hour = forecast.hourAt(LocalDateTime.of(2026, 10, 8, 7, 20))!!
        assertEquals(LocalTime.of(7, 0), hour.time.toLocalTime())
        assertNull(forecast.hourAt(LocalDateTime.of(2026, 10, 20, 7, 0)))
        assertEquals(12, forecast.hoursFrom(LocalDateTime.of(2026, 10, 8, 14, 35), 12).size)
        assertEquals(14, forecast.hoursFrom(LocalDateTime.of(2026, 10, 8, 14, 35), 12).first().time.hour)
    }

    @Test
    fun tripsSkipOnlineLessons() {
        val thursdayTrips = WeatherAdvice.trips(items(thursdayDate))
        assertEquals(listOf("Выход", "Обратно"), thursdayTrips.map { it.label })
        assertEquals(LocalTime.of(7, 20), thursdayTrips[0].time)
        assertEquals(LocalTime.of(18, 50), thursdayTrips[1].time)
        // В пятницу последняя пара онлайн, поэтому домой — после смены.
        assertEquals(LocalTime.of(15, 10), WeatherAdvice.trips(items(fridayDate))[1].time)
        assertTrue(WeatherAdvice.trips(emptyList()).isEmpty())
    }

    @Test
    fun clothingByFeelsLike() {
        assertEquals("можно в футболке", WeatherAdvice.clothing(25.0))
        assertEquals("тёплая куртка и шапка", WeatherAdvice.clothing(-3.6))
        assertEquals("зимняя куртка, шапка и перчатки", WeatherAdvice.clothing(-7.0))
        assertEquals("зимняя куртка, шапка, шарф и перчатки", WeatherAdvice.clothing(-18.0))
    }

    @Test
    fun adviceForOrdinaryDay() {
        val trips = WeatherAdvice.tripWeather(thursdayDate, items(thursdayDate), forecast)
        assertEquals(2, trips.size)
        assertEquals("Тёплая куртка и шапка.", WeatherAdvice.advice(trips, thursday))
        assertEquals("Выход в 7:20: +1°, ощущается −4°, пасмурно", WeatherAdvice.tripLine(trips[0]))
    }

    @Test
    fun adviceForSnowyWindyDay() {
        val trips = WeatherAdvice.tripWeather(fridayDate, items(fridayDate), forecast)
        val advice = WeatherAdvice.advice(trips, friday)!!
        assertTrue(advice, advice.startsWith("Зимняя куртка, шапка и перчатки"))
        assertTrue(advice, advice.contains("нужен капюшон"))
        assertTrue(advice, advice.contains("скользко"))
        assertTrue(advice, advice.contains("порывами до 16 м/с"))
        assertTrue(advice, !advice.contains("зонт"))
    }

    @Test
    fun alerts() {
        assertEquals(listOf("резкое похолодание"), WeatherAdvice.alerts(thursday, forecast.day(thursdayDate.minusDays(1))))
        assertEquals(listOf("сильный снег", "гололёд", "ветер до 16 м/с"), WeatherAdvice.alerts(friday, thursday))
    }

    @Test
    fun tomorrowNotification() {
        val note = WeatherAdvice.tomorrowNote(fridayDate, items(fridayDate), forecast)!!
        assertEquals("Завтра: сильный снег, гололёд, ветер до 16 м/с", note.title)
        assertTrue(note.text, note.text.contains("Выход в 7:20: −1°, ощущается −7°, снег, осадки 80%."))
        assertTrue(note.text, note.text.contains("нужен капюшон"))
        assertNull(WeatherAdvice.tomorrowNote(LocalDate.of(2026, 10, 20), emptyList(), forecast))
    }

    @Test
    fun morningSummaryIncludesRoadWeather() {
        val dayItems = items(thursdayDate)
        val text = Summaries.morning(thursdayDate, dayItems, PlanGenerator.planFor(thursdayDate, dayItems), forecast)
        assertTrue(text, text.contains("Погода: −1°…+5°, пасмурно."))
        assertTrue(text, text.contains("Выход в 7:20: +1°, ощущается −4°, пасмурно."))
        assertTrue(text, text.contains("Тёплая куртка и шапка."))
        assertTrue(text, text.contains("Внимание: резкое похолодание."))
    }

    @Test
    fun temperatureSigns() {
        assertEquals("0°", WeatherText.temperature(0.3))
        assertEquals("−12°", WeatherText.temperature(-11.6))
        assertEquals("+21°", WeatherText.temperature(20.5))
        assertEquals("−1°…+5°, пасмурно", WeatherText.short(thursday))
    }

    @Test
    fun brokenAnswerGivesNothing() {
        val broken = WeatherParser.parseForecast("""{"error":true,"reason":"bad"}""")
        assertTrue(broken.days.isEmpty())
        assertTrue(broken.hours.isEmpty())
        assertNull(broken.current)
    }
}
