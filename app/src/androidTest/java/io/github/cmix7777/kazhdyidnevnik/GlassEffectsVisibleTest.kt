package io.github.cmix7777.kazhdyidnevnik

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.cmix7777.kazhdyidnevnik.data.GlassMode
import io.github.cmix7777.kazhdyidnevnik.data.GlassWeather
import io.github.cmix7777.kazhdyidnevnik.ui.LocalDark
import io.github.cmix7777.kazhdyidnevnik.ui.fx.SkyFx
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberEffectTime
import io.github.cmix7777.kazhdyidnevnik.ui.fx.weatherGlass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Каждая погода на стекле правда что-то рисует: дождь, морось, снег (слабый и сильный) и иней.
 * Сравниваются кадры одного и того же экрана без погоды и с погодой — заметно отличаться должна заметная часть экрана.
 */
@RunWith(AndroidJUnit4::class)
class GlassEffectsVisibleTest {

    @get:Rule
    val compose = createComposeRule()

    private var glass by mutableStateOf(GlassWeather.NONE)

    private fun frame(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    /** Доля точек, которые заметно отличаются. */
    private fun changed(a: Bitmap, b: Bitmap): Double {
        var count = 0
        var total = 0
        for (y in 0 until minOf(a.height, b.height) step 2) {
            for (x in 0 until minOf(a.width, b.width) step 2) {
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                val d = abs((p shr 16 and 255) - (q shr 16 and 255)) + abs((p shr 8 and 255) - (q shr 8 and 255)) + abs((p and 255) - (q and 255))
                if (d > 40) count++
                total++
            }
        }
        return count.toDouble() / total
    }

    private fun check(weather: GlassWeather, minShare: Double) {
        compose.mainClock.autoAdvance = false
        glass = GlassWeather.NONE
        compose.setContent {
            CompositionLocalProvider(LocalDark provides true) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF16122A))
                        .weatherGlass(glass, rememberEffectTime(30))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(6) { Text("Пара ${it + 1}: Математика, ауд. 312", color = Color.White) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val before = frame()
        glass = weather
        // Погода проявляется за полторы секунды, потом снег успевает упасть, капли — набежать.
        compose.mainClock.advanceTimeBy(4_000)
        val after = frame()
        assertFalse("эффект на этом телефоне не собрался", SkyFx.glassBroken)
        val share = changed(before, after)
        assertTrue("${weather.mode} ${weather.strength}: изменилось только ${"%.2f".format(share * 100)}% экрана", share >= minShare)
    }

    @Test
    fun snowIsVisible() = check(GlassWeather(GlassMode.SNOW, 0.5f), 0.004)

    @Test
    fun heavySnowIsVisible() = check(GlassWeather(GlassMode.SNOW, 1f), 0.006)

    @Test
    fun rainIsVisible() = check(GlassWeather(GlassMode.RAIN, 0.75f), 0.004)

    @Test
    fun drizzleIsVisible() = check(GlassWeather(GlassMode.RAIN, 0.35f), 0.002)

    @Test
    fun frostIsVisible() = check(GlassWeather(GlassMode.FROST, 0.8f), 0.01)
}
