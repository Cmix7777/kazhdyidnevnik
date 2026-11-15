package io.github.cmix7777.kazhdyidnevnik

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.WallpaperChoice
import io.github.cmix7777.kazhdyidnevnik.data.Wallpapers
import io.github.cmix7777.kazhdyidnevnik.ui.Accents
import io.github.cmix7777.kazhdyidnevnik.ui.AppTheme
import io.github.cmix7777.kazhdyidnevnik.ui.ChatWallpaper
import io.github.cmix7777.kazhdyidnevnik.ui.LocalDark
import io.github.cmix7777.kazhdyidnevnik.ui.PersonTheme
import io.github.cmix7777.kazhdyidnevnik.ui.WallpaperDialog
import io.github.cmix7777.kazhdyidnevnik.ui.wallpaperCompileError
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Живые фоны переписки на настоящем Android: каждый собирается, рисует непустую картинку, движется
 * и в светлой теме остаётся светлым. Кадры сохраняются, чтобы посмотреть их глазами.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperTest {

    @get:Rule
    val compose = createComposeRule()

    private var choice by mutableStateOf(WallpaperChoice())
    private var time by mutableFloatStateOf(10f)
    private var dark by mutableStateOf(true)

    private fun frame(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Доля точек, которые заметно отличаются. */
    private fun changed(a: Bitmap, b: Bitmap): Double {
        var count = 0
        var total = 0
        for (y in 0 until minOf(a.height, b.height) step 2) {
            for (x in 0 until minOf(a.width, b.width) step 2) {
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                val d = abs((p shr 16 and 255) - (q shr 16 and 255)) + abs((p shr 8 and 255) - (q shr 8 and 255)) + abs((p and 255) - (q and 255))
                if (d > 24) count++
                total++
            }
        }
        return count.toDouble() / total
    }

    /** Сколько разных цветов на картинке (грубо, по 16 ступеней на канал). */
    private fun colors(bitmap: Bitmap): Int {
        val seen = HashSet<Int>()
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val p = bitmap.getPixel(x, y)
                seen += ((p shr 20 and 15) shl 8) or ((p shr 12 and 15) shl 4) or (p shr 4 and 15)
            }
        }
        return seen.size
    }

    /** Средняя яркость от 0 до 1. */
    private fun brightness(bitmap: Bitmap): Double {
        var sum = 0L
        var total = 0
        for (y in 0 until bitmap.height step 3) {
            for (x in 0 until bitmap.width step 3) {
                val p = bitmap.getPixel(x, y)
                sum += (p shr 16 and 255) + (p shr 8 and 255) + (p and 255)
                total++
            }
        }
        return sum / (total * 765.0)
    }

    @Test
    fun everyWallpaperCompilesDrawsMovesAndStaysLightInLightTheme() {
        assumeTrue("живые фоны есть с Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        compose.setContent {
            AppTheme {
                CompositionLocalProvider(LocalDark provides dark) {
                    Box(modifier = Modifier.size(180.dp, 390.dp).background(Accents.of(Person.AIZAT, dark).background)) {
                        ChatWallpaper(
                            choice = choice,
                            owner = Person.AIZAT,
                            running = false,
                            live = null,
                            modifier = Modifier.matchParentSize(),
                            clock = { time },
                        )
                    }
                }
            }
        }
        val problems = mutableListOf<String>()
        for (info in Wallpapers.ALL.filter { Wallpapers.live(it.id) }) {
            val error = wallpaperCompileError(info.id)
            if (error != null) {
                problems += "${info.id}: не собрался: ${error.take(400)}"
                continue
            }
            choice = WallpaperChoice(id = info.id)
            dark = true
            time = 10f
            compose.waitForIdle()
            val first = frame()
            time = 40f
            compose.waitForIdle()
            val second = frame()
            save("wall-${info.id}", second)
            dark = false
            compose.waitForIdle()
            val light = frame()
            save("wall-${info.id}-light", light)
            if (colors(first) < 10) problems += "${info.id}: пустой кадр, цветов ${colors(first)}"
            if (changed(first, second) < 0.01) problems += "${info.id}: не движется, изменилось ${changed(first, second)}"
            if (brightness(second) > 0.6) problems += "${info.id}: слишком яркий в тёмной теме, ${brightness(second)}"
            if (colors(light) < 4) problems += "${info.id}: в светлой теме пусто, цветов ${colors(light)}"
            if (brightness(light) < 0.6) problems += "${info.id}: в светлой теме тёмный, ${brightness(light)}"
        }
        assertEquals("", problems.joinToString("\n"))
    }

    @Test
    fun hueAndPowerChangeThePicture() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        compose.setContent {
            AppTheme {
                CompositionLocalProvider(LocalDark provides true) {
                    Box(modifier = Modifier.size(180.dp, 390.dp).background(Accents.of(Person.AIZAT, true).background)) {
                        ChatWallpaper(choice = choice, owner = Person.AIZAT, running = false, live = null, modifier = Modifier.matchParentSize(), clock = { 20f })
                    }
                }
            }
        }
        choice = WallpaperChoice(id = "aurora")
        compose.waitForIdle()
        val plain = frame()
        choice = WallpaperChoice(id = "aurora", hue = 0.5f)
        compose.waitForIdle()
        val shifted = frame()
        choice = WallpaperChoice(id = "aurora", power = Wallpapers.POWER_MIN)
        compose.waitForIdle()
        val dim = frame()
        assertEquals("оттенок меняет картинку", true, changed(plain, shifted) > 0.05)
        assertEquals("яркость убавляет свет", true, brightness(dim) < brightness(plain))
    }

    @Test
    fun pickerChoosesWallpaperAndSwitches() {
        var current by mutableStateOf(WallpaperChoice())
        val saved = mutableListOf<WallpaperChoice>()
        var closed = false
        compose.setContent {
            AppTheme {
                CompositionLocalProvider(LocalDark provides true) {
                    PersonTheme(Person.AIZAT) {
                        WallpaperDialog(
                            choice = current,
                            owner = Person.AIZAT,
                            onChange = { value, final ->
                                current = value
                                if (final) saved += value
                            },
                            onClose = { closed = true },
                        )
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("wall-list")).fetchSemanticsNodes().isNotEmpty() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            compose.onNodeWithTag("wall-list").performScrollToNode(hasTestTag("wall-retro"))
            compose.onNodeWithTag("wall-retro").performClick()
            compose.waitUntil(5_000) { current.id == "retro" }
            compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("wall-tilt")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("wall-tilt").performClick()
            compose.waitUntil(5_000) { !current.tilt }
            assertEquals(WallpaperChoice(id = "retro", tilt = false), saved.last())
        }
        compose.onNodeWithTag("wall-list").performScrollToNode(hasTestTag("wall-none"))
        compose.onNodeWithTag("wall-none").performClick()
        compose.waitUntil(5_000) { current.id == Wallpapers.NONE }
        compose.onNodeWithTag("wall-done").performClick()
        compose.waitUntil(5_000) { closed }
    }
}
