package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.Person

/**
 * Цвета одного человека. У Айзата — тёмный фиолетовый, у Насти — сиреневый, небесно-голубой и белый.
 * Имена полей как в палитре Айзата: [primary] — главный акцент, [light] — светлый акцент для текста.
 */
@Immutable
data class Accent(
    val background: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val primary: Color,
    val deep: Color,
    val dark: Color,
    val glow: Color,
    val glowLow: Color,
    val light: Color,
    val highlightTop: Color,
    val highlightBottom: Color,
)

object Accents {
    val Aizat = Accent(
        background = Color(0xFF0A0610),
        cardTop = Color(0xFF1C1429),
        cardBottom = Color(0xFF120C1B),
        primary = Color(0xFFA855F7),
        deep = Color(0xFF7C3AED),
        dark = Color(0xFF2E1065),
        glow = Color(0xFF6D28D9),
        glowLow = Color(0xFF7E22CE),
        light = Color(0xFFC9B8FF),
        highlightTop = Color(0xFF6D28D9),
        highlightBottom = Color(0xFF3B0F73),
    )

    val Nastya = Accent(
        background = Color(0xFF070A12),
        cardTop = Color(0xFF171B2E),
        cardBottom = Color(0xFF0E1221),
        primary = Color(0xFFB08CF0),
        deep = Color(0xFF4FA3E8),
        dark = Color(0xFF1B2D55),
        glow = Color(0xFF8F6FE0),
        glowLow = Color(0xFF3D8FE0),
        light = Color(0xFFBFE3FF),
        highlightTop = Color(0xFF7D62D6),
        highlightBottom = Color(0xFF24508F),
    )

    fun of(person: Person): Accent = if (person == Person.AIZAT) Aizat else Nastya

    /** Плавный переход между палитрами при свайпе: 0 — [from], 1 — [to]. */
    fun blend(from: Accent, to: Accent, fraction: Float): Accent {
        val t = fraction.coerceIn(0f, 1f)
        return Accent(
            background = lerp(from.background, to.background, t),
            cardTop = lerp(from.cardTop, to.cardTop, t),
            cardBottom = lerp(from.cardBottom, to.cardBottom, t),
            primary = lerp(from.primary, to.primary, t),
            deep = lerp(from.deep, to.deep, t),
            dark = lerp(from.dark, to.dark, t),
            glow = lerp(from.glow, to.glow, t),
            glowLow = lerp(from.glowLow, to.glowLow, t),
            light = lerp(from.light, to.light, t),
            highlightTop = lerp(from.highlightTop, to.highlightTop, t),
            highlightBottom = lerp(from.highlightBottom, to.highlightBottom, t),
        )
    }
}

/** Палитра того, чья страница сейчас рисуется. */
val LocalAccent = compositionLocalOf { Accents.Aizat }

/**
 * Цвета приложения. Нейтральные (текст, рамки) общие, акцентные берутся из палитры человека,
 * поэтому их можно читать только внутри @Composable.
 */
object Palette {
    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current.background
    val CardTop: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current.cardTop
    val CardBottom: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current.cardBottom
    val Violet: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current.primary
    val VioletDeep: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current.deep
    val VioletDark: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current.dark
    val Glow: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current.glow
    val Lavender: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current.light
    val Border = Color(0x17FFFFFF)
    val BorderStrong = Color(0x2BFFFFFF)
    val Chip = Color(0x0FFFFFFF)
    val Text = Color(0xFFF5F3FA)
    val TextMuted = Color(0xFFA79DBA)
    val TextFaint = Color(0xFF6F6582)
    val Danger = Color(0xFFFF6B81)
    val Ink = Color(0xFF14101C)
}

/** Manrope: геометрический шрифт с кириллицей, один файл на все начертания. */
private val Manrope = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal),
    Font(R.font.manrope, weight = FontWeight.Medium),
    Font(R.font.manrope, weight = FontWeight.SemiBold),
    Font(R.font.manrope, weight = FontWeight.Bold),
    Font(R.font.manrope, weight = FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(52, FontWeight.Bold, 58, -0.03),
    displayMedium = style(44, FontWeight.Bold, 50, -0.03),
    displaySmall = style(36, FontWeight.Bold, 42, -0.025),
    headlineLarge = style(34, FontWeight.Bold, 40, -0.025),
    headlineMedium = style(28, FontWeight.Bold, 34, -0.02),
    headlineSmall = style(24, FontWeight.Bold, 30, -0.015),
    titleLarge = style(20, FontWeight.Bold, 26, -0.01),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 23),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 17),
    labelLarge = style(14, FontWeight.SemiBold, 20),
    labelMedium = style(12, FontWeight.SemiBold, 16),
    labelSmall = style(11, FontWeight.Medium, 15),
)

private fun appColors(accent: Accent) = darkColorScheme(
    primary = accent.primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1767),
    onPrimaryContainer = Color(0xFFEBDDFF),
    inversePrimary = Color(0xFF6D28D9),
    secondary = accent.light,
    onSecondary = Color(0xFF1E1037),
    secondaryContainer = Color(0xFF2A1E44),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = Color(0xFFF0ABFC),
    onTertiary = Color(0xFF3B0A45),
    tertiaryContainer = Color(0xFF3A1840),
    onTertiaryContainer = Color(0xFFFCD9FF),
    background = accent.background,
    onBackground = Palette.Text,
    surface = accent.background,
    onSurface = Palette.Text,
    surfaceVariant = Color(0xFF241B33),
    onSurfaceVariant = Palette.TextMuted,
    surfaceTint = accent.primary,
    inverseSurface = Color(0xFFEDE7F6),
    inverseOnSurface = Color(0xFF1A1326),
    error = Palette.Danger,
    onError = Color(0xFF3D0A14),
    errorContainer = Color(0xFF3D1220),
    onErrorContainer = Color(0xFFFFD6DD),
    outline = Color(0xFF4A3F5E),
    outlineVariant = Color(0xFF2C2340),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2E2540),
    surfaceContainer = Color(0xFF1A1326),
    surfaceContainerHigh = Color(0xFF211930),
    surfaceContainerHighest = Color(0xFF2A2139),
    surfaceContainerLow = Color(0xFF151020),
    surfaceContainerLowest = Color(0xFF0E0915),
    surfaceDim = accent.background,
)

/** Тема приложения: всегда тёмная, в стиле референса (тёмный фиолетовый, стекло, свечение). */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = appColors(LocalAccent.current), typography = AppTypography, content = content)
}

/** Тема страницы одного человека: его палитра для всего, что внутри. */
@Composable
fun PersonTheme(person: Person, content: @Composable () -> Unit) {
    AccentTheme(Accents.of(person), content)
}

@Composable
fun AccentTheme(accent: Accent, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = appColors(accent), typography = AppTypography, content = content)
    }
}
