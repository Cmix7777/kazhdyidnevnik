package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Солнце и Луна над Ижевском. Считается прямо на телефоне по астрономическим формулам, без интернета:
 * время рассвета и заката (точность в пределах пары минут) и фаза Луны.
 */
object Astronomy {
    const val IZHEVSK_LAT = 56.85
    const val IZHEVSK_LON = 53.20

    /** Ижевск живёт по самарскому времени, UTC+4, без перехода на летнее. */
    const val IZHEVSK_UTC_OFFSET = 4

    private fun rad(deg: Double) = deg * PI / 180.0
    private fun deg(rad: Double) = rad * 180.0 / PI

    /**
     * Рассвет и закат в местном времени (часы от полуночи, например 6.87 — это 6:52).
     * null — в этот день Солнце не встаёт или не садится (в Ижевске такого не бывает).
     */
    fun sun(date: LocalDate, lat: Double = IZHEVSK_LAT, lon: Double = IZHEVSK_LON, utcOffset: Int = IZHEVSK_UTC_OFFSET): Pair<Double, Double>? {
        val rise = sunEvent(date, lat, lon, utcOffset, rising = true) ?: return null
        val set = sunEvent(date, lat, lon, utcOffset, rising = false) ?: return null
        return rise to set
    }

    private fun sunEvent(date: LocalDate, lat: Double, lon: Double, utcOffset: Int, rising: Boolean): Double? {
        val n = date.dayOfYear
        val lngHour = lon / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634
        l = ((l % 360) + 360) % 360
        var ra = deg(atan(0.91764 * tan(rad(l))))
        ra = ((ra % 360) + 360) % 360
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(90.833)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360.0 - deg(acos(cosH)) else deg(acos(cosH))) / 15.0
        val localT = h + ra - 0.06571 * t - 6.622
        val ut = ((localT - lngHour) % 24 + 24) % 24
        return ((ut + utcOffset) % 24 + 24) % 24
    }

    private val referenceNewMoon: LocalDateTime = LocalDateTime.of(2000, 1, 6, 18, 14)
    private const val SYNODIC_DAYS = 29.530588853

    /** Фаза Луны от 0 до 1: 0 — новолуние, 0,5 — полнолуние, между ними растущая, после — убывающая. */
    fun moonPhase(atUtc: LocalDateTime): Double {
        val days = ChronoUnit.SECONDS.between(referenceNewMoon, atUtc) / 86_400.0
        val phase = (days % SYNODIC_DAYS) / SYNODIC_DAYS
        return if (phase < 0) phase + 1 else phase
    }

    /** Фаза Луны для местного времени Ижевска. */
    fun moonPhaseLocal(local: LocalDateTime): Double =
        moonPhase(local.atOffset(ZoneOffset.ofHours(IZHEVSK_UTC_OFFSET)).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime())

    /** Время как часы от полуночи: 17:34 → 17.57. */
    fun hours(time: LocalTime): Double = time.hour + time.minute / 60.0 + time.second / 3600.0
}

/** Что сейчас на небе: для живого фона приложения. */
data class SkyState(
    val hour: Double,
    val sunrise: Double,
    val sunset: Double,
    val moonPhase: Double,
    /** Облачность от 0 до 1 (из прогноза; если прогноза нет — 0,3). */
    val clouds: Double,
) {
    companion object {
        fun at(now: LocalDateTime, forecast: Forecast?): SkyState {
            val (rise, set) = Astronomy.sun(now.toLocalDate()) ?: (7.0 to 18.0)
            val cover = forecast?.current?.cloudCover ?: forecast?.hourAt(now)?.cloudCover
            return SkyState(
                hour = Astronomy.hours(now.toLocalTime()),
                sunrise = rise,
                sunset = set,
                moonPhase = Astronomy.moonPhaseLocal(now),
                clouds = (cover ?: 30) / 100.0,
            )
        }
    }
}

/** Погода на стекле экрана «Сегодня». */
enum class GlassMode { NONE, RAIN, SNOW, FROST }

data class GlassWeather(val mode: GlassMode, val strength: Float) {
    companion object {
        val NONE = GlassWeather(GlassMode.NONE, 0f)

        /** Мороз на стекле появляется от этой температуры и ниже, если нет осадков. */
        const val FROST_FROM = -8.0

        /** По погоде сейчас: дождь и морось, снег, мороз или ничего. Сила зависит от того, насколько сильные осадки. */
        fun from(forecast: Forecast?, now: LocalDateTime): GlassWeather {
            val current = forecast?.current
            val hour = forecast?.hourAt(now)
            val code = current?.code ?: hour?.code ?: return NONE
            val temperature = current?.temperature ?: hour?.temperature
            return when (code) {
                51, 53, 55, 56, 57 -> GlassWeather(GlassMode.RAIN, 0.35f)
                61, 80, 66 -> GlassWeather(GlassMode.RAIN, 0.55f)
                63, 81, 67 -> GlassWeather(GlassMode.RAIN, 0.75f)
                65, 82, 95, 96, 99 -> GlassWeather(GlassMode.RAIN, 1f)
                71, 77, 85 -> GlassWeather(GlassMode.SNOW, 0.5f)
                73 -> GlassWeather(GlassMode.SNOW, 0.75f)
                75, 86 -> GlassWeather(GlassMode.SNOW, 1f)
                else -> if (temperature != null && temperature <= FROST_FROM) {
                    GlassWeather(GlassMode.FROST, (0.5 + (FROST_FROM - temperature) / 20.0).coerceIn(0.5, 1.0).toFloat())
                } else {
                    NONE
                }
            }
        }
    }
}
