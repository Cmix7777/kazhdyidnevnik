package io.github.cmix7777.kazhdyidnevnik.ui.fx

import io.github.cmix7777.kazhdyidnevnik.data.WallpaperChoice
import io.github.cmix7777.kazhdyidnevnik.data.Wallpapers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Список фонов переписки и тексты их шейдеров сходятся; выбор фона приводится к допустимому. */
class WallpaperSourcesTest {

    @Test
    fun everyLiveWallpaperHasASceneAndEverySceneIsInTheList() {
        val ids = Wallpapers.ALL.map { it.id }
        assertEquals("названия повторяются", ids.size, ids.toSet().size)
        assertEquals(Wallpapers.SMOKE, ids.first())
        assertEquals(Wallpapers.NONE, ids.last())
        val live = ids.filter { Wallpapers.live(it) }
        assertEquals(WALLPAPER_SCENES.keys.toList(), live)
        assertEquals(16, live.size)
        assertNull(wallpaperSource(Wallpapers.SMOKE))
        assertNull(wallpaperSource(Wallpapers.NONE))
        assertNull(wallpaperSource("нет такого"))
        assertFalse(Wallpapers.live("нет такого"))
        Wallpapers.ALL.forEach { assertTrue(it.title.isNotBlank()) }
    }

    @Test
    fun everySourceIsACompleteShader() {
        for (id in WALLPAPER_SCENES.keys) {
            val source = wallpaperSource(id)
            assertNotNull(id, source)
            source!!
            assertEquals("$id: одна сцена", 1, Regex("float4 scene\\(float2 uv, float2 p, float t\\)").findAll(source).count())
            assertEquals("$id: один main", 1, Regex("half4 main\\(float2 fc\\)").findAll(source).count())
            assertEquals("$id: один sstep", 1, Regex("float sstep\\(float a, float b, float x\\)").findAll(source).count())
            for (uniform in listOf("uSize", "uTime", "uDark", "uBg", "uInk", "uHue", "uPower", "uTilt", "uTouch", "uPulse")) {
                assertTrue("$id: нет $uniform", Regex("uniform float[234]? $uniform;").containsMatchIn(source))
            }
            // Встроенный smoothstep и степень от отрицательного числа на части телефонов считаются по-разному.
            assertFalse("$id: smoothstep", source.contains("smoothstep("))
            assertTrue("$id: скобки", source.count { it == '{' } == source.count { it == '}' })
        }
    }

    @Test
    fun choiceIsBroughtIntoLimits() {
        assertEquals(WallpaperChoice(), Wallpapers.clean(WallpaperChoice()))
        assertEquals(Wallpapers.SMOKE, Wallpapers.clean(WallpaperChoice(id = "удалённый фон")).id)
        val wild = Wallpapers.clean(WallpaperChoice(id = "retro", hue = 1.25f, power = 9f, speed = -3f))
        assertEquals("retro", wild.id)
        assertEquals(0.25f, wild.hue, 0.0001f)
        assertEquals(Wallpapers.POWER_MAX, wild.power, 0f)
        assertEquals(Wallpapers.SPEED_MIN, wild.speed, 0f)
        val broken = Wallpapers.clean(WallpaperChoice(id = "code", hue = Float.NaN, power = Float.POSITIVE_INFINITY, speed = Float.NaN))
        assertEquals(WallpaperChoice(id = "code"), broken)
        assertEquals(0.75f, Wallpapers.clean(WallpaperChoice(hue = -0.25f)).hue, 0.0001f)
    }

    @Test
    fun weakerPhoneGetsCoarserAndSlowerBackground() {
        for (info in Wallpapers.ALL.filter { Wallpapers.live(it.id) }) {
            var scale = 0f
            var fps = Int.MAX_VALUE
            for (level in 0..Wallpapers.MAX_LEVEL) {
                val (s, f) = Wallpapers.quality(info, level)
                assertTrue("${info.id}: размер", s >= scale)
                assertTrue("${info.id}: кадры", f in 12..fps)
                scale = s
                fps = f
            }
            assertEquals(Wallpapers.quality(info, Wallpapers.MAX_LEVEL), Wallpapers.quality(info, 99))
            assertEquals(info.scale to info.fps, Wallpapers.quality(info, 0))
        }
    }
}
