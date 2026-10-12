package io.github.cmix7777.kazhdyidnevnik.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.data.SpecialDates
import io.github.cmix7777.kazhdyidnevnik.data.SpecialKind
import io.github.cmix7777.kazhdyidnevnik.data.SpecialToday
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.ui.fx.ANNIVERSARY_AGSL
import io.github.cmix7777.kazhdyidnevnik.ui.fx.BIRTHDAY_AGSL
import io.github.cmix7777.kazhdyidnevnik.ui.fx.EffectsSettings
import io.github.cmix7777.kazhdyidnevnik.ui.fx.Haptics
import io.github.cmix7777.kazhdyidnevnik.ui.fx.NEW_YEAR_AGSL
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Показывает анимацию особого дня, если сегодня он есть и ещё не был показан на этом телефоне. */
@Composable
fun SpecialDayHost(diary: DiaryViewModel) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val now by rememberNow()
    val today = now.toLocalDate()
    var shown by remember { mutableStateOf(settings.specialShown) }
    val special = SpecialDates.today(diary.specialDates(), today)
    if (special != null && special.key != shown) {
        SpecialDayOverlay(special, today, onDone = {
            settings.specialShown = special.key
            shown = special.key
        })
    }
}

/** Длительность анимации особого дня и когда показывать поздравление (секунды). */
private class SpecialTiming(val total: Float, val textFrom: Float, val textTo: Float)

private fun timing(kind: SpecialKind) = when (kind) {
    SpecialKind.ANNIVERSARY -> SpecialTiming(3.5f, 1.0f, 1.95f)
    SpecialKind.BIRTHDAY -> SpecialTiming(3.6f, 1.8f, 2.85f)
    SpecialKind.NEW_YEAR -> SpecialTiming(4.3f, 2.0f, 2.9f)
}

/**
 * Особый день: при первом открытии приложения за день поверх всего проигрывается анимация повода
 * и поздравление. Касание пропускает её. Без шейдеров или в режиме «меньше анимаций» — просто поздравление.
 */
@Composable
fun SpecialDayOverlay(special: SpecialToday, today: LocalDate, onDone: () -> Unit) {
    val context = LocalContext.current
    val animate = remember { EffectsSettings.animate(context) }
    var shaders by remember { mutableStateOf(animate && EffectsSettings.shadersSupported) }
    val timing = remember(special.kind) { timing(special.kind) }
    val t = remember { Animatable(0f) }
    var glued by remember { mutableStateOf(false) }

    val date = special.date
    val years = date?.yearsAt(today)
    val title = when (special.kind) {
        SpecialKind.ANNIVERSARY -> "С годовщиной"
        SpecialKind.BIRTHDAY -> date?.whose?.let { "С днём рождения, ${it.shortName}" } ?: (date?.shownTitle ?: "С днём рождения")
        SpecialKind.NEW_YEAR -> "С Новым годом"
    }
    val subtitle = when (special.kind) {
        SpecialKind.ANNIVERSARY -> date?.yearsText(today) ?: "сегодня ваш особенный день"
        SpecialKind.BIRTHDAY -> years?.let { "исполняется $it" } ?: "пусть этот год будет лучшим"
        SpecialKind.NEW_YEAR -> "пусть ${today.year} будет вашим"
    }
    val colorA: Color
    val colorB: Color
    when (special.kind) {
        SpecialKind.BIRTHDAY -> {
            val whose = date?.whose
            colorA = whose?.let { TogetherColors.top(it) } ?: Color(0xFFA855F7)
            colorB = whose?.let { TogetherColors.bottom(it) } ?: Color(0xFFC9B8FF)
        }
        else -> {
            colorA = TogetherColors.aizatTop
            colorB = TogetherColors.nastyaTop
        }
    }

    LaunchedEffect(Unit) {
        if (animate) {
            t.animateTo(timing.total, tween((timing.total * 1000).toInt(), easing = LinearEasing)) {
                // Годовщина — это тоже склейка: в момент слияния мягкий «чпок».
                if (special.kind == SpecialKind.ANNIVERSARY && !glued && value >= 1.0f) {
                    glued = true
                    Haptics.glue(context)
                }
            }
        } else {
            delay(2_200)
        }
        onDone()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDone() } },
    ) {
        if (shaders && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Эффект мягкий (капли, свечение, снег), поэтому считаем его в уменьшенном в 3 раза слое и растягиваем:
            // в 9 раз меньше работы видеокарте, и анимация не тормозит даже на средних телефонах.
            val scale = SHADER_SCALE
            SpecialShaderLayer(
                kind = special.kind,
                t = { t.value },
                colorA = colorA,
                colorB = colorB,
                years = years,
                year = today.year,
                scale = scale,
                onFail = { shaders = false },
                modifier = Modifier
                    .size(maxWidth / scale, maxHeight / scale)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) { drawRect(Color(0xF2080510)) }
        }
        val textAlpha = { if (!animate) 1f else textVisibility(t.value, timing) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.2f, start = 24.dp, end = 24.dp)
                .graphicsLayer {
                    val v = textAlpha()
                    alpha = v
                    translationY = (1f - v) * 8.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD8D0E8), textAlign = TextAlign.Center)
        }
    }
}

/** Во сколько раз уменьшен слой шейдера особого дня. */
private const val SHADER_SCALE = 3f

private fun textVisibility(t: Float, timing: SpecialTiming): Float {
    val fadeIn = ((t - timing.textFrom) / 0.3f).coerceIn(0f, 1f)
    val fadeOut = ((timing.textTo - t) / 0.3f).coerceIn(0f, 1f)
    return minOf(fadeIn, fadeOut)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun SpecialShaderLayer(
    kind: SpecialKind,
    t: () -> Float,
    colorA: Color,
    colorB: Color,
    years: Int?,
    year: Int,
    scale: Float,
    onFail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shader = remember(kind) {
        runCatching {
            RuntimeShader(
                when (kind) {
                    SpecialKind.ANNIVERSARY -> ANNIVERSARY_AGSL
                    SpecialKind.BIRTHDAY -> BIRTHDAY_AGSL
                    SpecialKind.NEW_YEAR -> NEW_YEAR_AGSL
                },
            )
        }.getOrNull()
    }
    if (shader == null) {
        LaunchedEffect(Unit) { onFail() }
        return
    }
    // Возраст цифрами, если он известен и двузначный или меньше; иначе сердце.
    val age = years?.takeIf { it in 1..99 }
    val digits = if (age == null) -1f to -1f else (if (age >= 10) (age / 10).toFloat() else -1f) to (age % 10).toFloat()
    val yearDigits = year.toString().padStart(4, '0').map { it.digitToInt().toFloat() }
    Canvas(modifier = modifier) {
        try {
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uDp", density / scale)
            shader.setFloatUniform("uT", t())
            when (kind) {
                SpecialKind.ANNIVERSARY -> {
                    shader.setFloatUniform("uCA", colorA.red, colorA.green, colorA.blue)
                    shader.setFloatUniform("uCB", colorB.red, colorB.green, colorB.blue)
                }
                SpecialKind.BIRTHDAY -> {
                    shader.setFloatUniform("uC", colorA.red, colorA.green, colorA.blue)
                    shader.setFloatUniform("uC2", colorB.red, colorB.green, colorB.blue)
                    shader.setFloatUniform("uDig", digits.first, digits.second)
                    shader.setFloatUniform("uHeart", if (age == null) 1f else 0f)
                }
                SpecialKind.NEW_YEAR -> {
                    shader.setFloatUniform("uYear", yearDigits[0], yearDigits[1], yearDigits[2], yearDigits[3])
                    shader.setFloatUniform("uCA", colorA.red, colorA.green, colorA.blue)
                    shader.setFloatUniform("uCB", colorB.red, colorB.green, colorB.blue)
                }
            }
            drawRect(brush = ShaderBrush(shader))
        } catch (e: RuntimeException) {
            onFail()
        }
    }
}
