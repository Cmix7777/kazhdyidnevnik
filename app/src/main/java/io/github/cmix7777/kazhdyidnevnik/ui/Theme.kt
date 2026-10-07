package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.cmix7777.kazhdyidnevnik.R

/** Цвета приложения: почти чёрный фон с фиолетовым оттенком и яркий фиолетовый акцент. */
object Palette {
    val Background = Color(0xFF0A0610)
    val CardTop = Color(0xFF1C1429)
    val CardBottom = Color(0xFF120C1B)
    val Border = Color(0x17FFFFFF)
    val BorderStrong = Color(0x2BFFFFFF)
    val Chip = Color(0x0FFFFFFF)
    val Violet = Color(0xFFA855F7)
    val VioletDeep = Color(0xFF7C3AED)
    val VioletDark = Color(0xFF2E1065)
    val Glow = Color(0xFF6D28D9)
    val Lavender = Color(0xFFC9B8FF)
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

private val AppColors = darkColorScheme(
    primary = Palette.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1767),
    onPrimaryContainer = Color(0xFFEBDDFF),
    inversePrimary = Color(0xFF6D28D9),
    secondary = Palette.Lavender,
    onSecondary = Color(0xFF1E1037),
    secondaryContainer = Color(0xFF2A1E44),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = Color(0xFFF0ABFC),
    onTertiary = Color(0xFF3B0A45),
    tertiaryContainer = Color(0xFF3A1840),
    onTertiaryContainer = Color(0xFFFCD9FF),
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Background,
    onSurface = Palette.Text,
    surfaceVariant = Color(0xFF241B33),
    onSurfaceVariant = Palette.TextMuted,
    surfaceTint = Palette.Violet,
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
    surfaceDim = Palette.Background,
)

/** Тема приложения: всегда тёмная, в стиле референса (тёмный фиолетовый, стекло, свечение). */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, content = content)
}
