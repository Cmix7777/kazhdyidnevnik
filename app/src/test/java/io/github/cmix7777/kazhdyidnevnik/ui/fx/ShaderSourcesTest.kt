package io.github.cmix7777.kazhdyidnevnik.ui.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Эффекты на видеокартах разных телефонов. Встроенный smoothstep с первым краем больше второго
 * по стандарту не определён: на одних телефонах снег, капли и звёзды рисуются, на других пропадают.
 * Здесь проверяется, что такого smoothstep нет ни в одном эффекте, а замена считает одинаково при любом порядке краёв.
 */
class ShaderSourcesTest {

    private val sources: List<File> = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun noEffectUsesBuiltInSmoothstep() {
        assertTrue("исходники не найдены", sources.size > 20)
        val offenders = sources.filter { file -> file.readText().contains("smoothstep(") }.map { it.name }
        assertEquals("smoothstep остался в: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun everyEffectHasTheSafeHelper() {
        for (shader in listOf(WEATHER_GLASS_AGSL, SKY_AGSL, INK_AGSL, GLUE_AGSL, DROP_AGSL, ANNIVERSARY_AGSL, BIRTHDAY_AGSL, NEW_YEAR_AGSL)) {
            if (shader.contains("sstep(")) assertTrue(shader.contains("float sstep(float a, float b, float x)"))
        }
        val chat = sources.single { it.name == "ChatFx.kt" }.readText()
        assertTrue(chat.contains("SMOKE_AGSL = SSTEP_AGSL +"))
    }

    /** Та же формула, что в SSTEP_AGSL. */
    private fun sstep(a: Float, b: Float, x: Float): Float {
        val w = b - a
        if (kotlin.math.abs(w) < 0.000001f) return 0f
        val t = ((x - a) / w).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    @Test
    fun reversedEdgesWorkTheSameEverywhere() {
        // Снежинка радиусом 0,1: в центре полностью видна, за краем пропадает, между — плавно.
        val r = 0.1f
        assertEquals(1f, sstep(r, r * 0.2f, 0f), 0.0001f)
        assertEquals(0f, sstep(r, r * 0.2f, 0.2f), 0.0001f)
        for (x in listOf(0.03f, 0.05f, 0.07f)) {
            assertEquals(1f - sstep(r * 0.2f, r, x), sstep(r, r * 0.2f, x), 0.0001f)
        }
        // Нулевой радиус (капля только родилась) — ничего, а не вспышка на всю клетку.
        assertEquals(0f, sstep(0f, 0f, 0.5f), 0f)
    }
}
