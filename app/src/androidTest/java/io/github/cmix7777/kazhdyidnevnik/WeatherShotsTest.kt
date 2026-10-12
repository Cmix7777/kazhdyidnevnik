package io.github.cmix7777.kazhdyidnevnik

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cmix7777.kazhdyidnevnik.data.GlassMode
import io.github.cmix7777.kazhdyidnevnik.data.GlassWeather
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.SkyState
import io.github.cmix7777.kazhdyidnevnik.ui.AppTheme
import io.github.cmix7777.kazhdyidnevnik.ui.GlassCard
import io.github.cmix7777.kazhdyidnevnik.ui.GlowBackground
import io.github.cmix7777.kazhdyidnevnik.ui.LocalDark
import io.github.cmix7777.kazhdyidnevnik.ui.Palette
import io.github.cmix7777.kazhdyidnevnik.ui.PersonTheme
import io.github.cmix7777.kazhdyidnevnik.ui.Pill
import io.github.cmix7777.kazhdyidnevnik.ui.TwoToneTitle
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberEffectTime
import io.github.cmix7777.kazhdyidnevnik.ui.fx.weatherGlass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Кадры живого неба в разное время суток и погоды на стекле (дождь, снег, мороз) на простом экране. */
@RunWith(AndroidJUnit4::class)
class WeatherShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun save(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // 12 октября в Ижевске: рассвет 6:52, закат 17:34.
    private fun sky(hour: Double, moon: Double = 0.5, clouds: Double = 0.15) =
        SkyState(hour = hour, sunrise = 6.87, sunset = 17.57, moonPhase = moon, clouds = clouds)

    private fun show(sky: SkyState, glass: GlassWeather = GlassWeather.NONE) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppTheme {
                CompositionLocalProvider(LocalDark provides true) {
                    PersonTheme(Person.AIZAT) {
                        GlowBackground(sky = remember { mutableStateOf(sky) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weatherGlass(glass, rememberEffectTime(30))
                                    .statusBarsPadding()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Pill(text = "Сегодня", accent = true)
                                TwoToneTitle(first = "Понедельник", second = "12 октября")
                                Pill(text = "3 пары, работа с 15:00")
                                repeat(4) { index ->
                                    GlassCard {
                                        Text("Пара ${index + 1}: Математика", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                                        Text("ауд. 312, с 9:00 до 10:30", color = Palette.TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shoot(name: String, sky: SkyState, glass: GlassWeather = GlassWeather.NONE, ms: Long = 2_000) {
        show(sky, glass)
        compose.mainClock.advanceTimeBy(ms)
        save(name)
    }

    @Test
    fun skyNightFullMoon() = shoot("sky_1_night", sky(hour = 23.5, moon = 0.5, clouds = 0.1))

    @Test
    fun skyNightCrescentCloudy() = shoot("sky_2_night_clouds", sky(hour = 2.0, moon = 0.15, clouds = 0.6))

    @Test
    fun skyDawn() = shoot("sky_3_dawn", sky(hour = 7.1, moon = 0.3, clouds = 0.3))

    @Test
    fun skyDay() = shoot("sky_4_day", sky(hour = 12.5, clouds = 0.45))

    @Test
    fun skySunset() = shoot("sky_5_sunset", sky(hour = 17.4, clouds = 0.5))

    @Test
    fun skyEvening() = shoot("sky_6_evening", sky(hour = 19.0, moon = 0.04, clouds = 0.05))

    @Test
    fun rain() = shoot("glass_1_rain", sky(hour = 20.0, clouds = 0.9), GlassWeather(GlassMode.RAIN, 0.75f))

    @Test
    fun heavyRain() = shoot("glass_2_rain_heavy", sky(hour = 13.0, clouds = 1.0), GlassWeather(GlassMode.RAIN, 1f), ms = 3_500)

    @Test
    fun snow() = shoot("glass_3_snow", sky(hour = 22.0, clouds = 0.8), GlassWeather(GlassMode.SNOW, 0.75f))

    @Test
    fun frost() = shoot("glass_4_frost", sky(hour = 8.5, clouds = 0.1), GlassWeather(GlassMode.FROST, 0.8f))

    @Test
    fun frostWiped() {
        show(sky(hour = 8.5, clouds = 0.1), GlassWeather(GlassMode.FROST, 0.8f))
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.08f, height * 0.12f))
            moveTo(Offset(width * 0.3f, height * 0.16f))
            moveTo(Offset(width * 0.55f, height * 0.13f))
            moveTo(Offset(width * 0.85f, height * 0.17f))
            up()
        }
        compose.mainClock.advanceTimeBy(300)
        save("glass_5_frost_wiped")
    }
}
