package io.github.cmix7777.kazhdyidnevnik.ui.fx

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import io.github.cmix7777.kazhdyidnevnik.data.Forecast
import io.github.cmix7777.kazhdyidnevnik.data.GlassMode
import io.github.cmix7777.kazhdyidnevnik.data.GlassWeather
import io.github.cmix7777.kazhdyidnevnik.data.SkyState
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * Время для бесконечных эффектов, не чаще [fps] раз в секунду. Если [running] выключен («меньше анимаций»),
 * время стоит и картинка не перерисовывается. В автотестах такие анимации тоже стоят.
 */
@Composable
internal fun rememberEffectTime(fps: Int, running: Boolean = true): () -> Float {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fps, running) {
        if (!running) return@LaunchedEffect
        val step = 1_000_000_000L / fps
        val offset = time
        val start = withInfiniteAnimationFrameNanos { it }
        var last = start
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (now - last >= step) {
                    last = now
                    time = offset + (now - start) / 1_000_000_000f
                }
            }
        }
    }
    return { time }
}

/**
 * Погода на стекле поверх содержимого (Android 13+): дождь, снег или мороз по прогнозу.
 * Если погода обычная, телефон старый или включено «меньше анимаций» — ничего не меняет.
 */
@Composable
fun Modifier.weatherGlass(glass: GlassWeather): Modifier {
    val context = LocalContext.current
    if (glass.mode == GlassMode.NONE || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !EffectsSettings.animate(context)) {
        return this
    }
    return this.then(weatherGlassLayer(glass))
}

private class Wipe(val x: Float, val y: Float, val at: Float)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun weatherGlassLayer(glass: GlassWeather): Modifier {
    val shader = remember { runCatching { RuntimeShader(WEATHER_GLASS_AGSL) }.getOrNull() }
    var failed by remember { mutableStateOf(false) }
    if (shader == null || failed) return Modifier
    val density = LocalDensity.current.density
    // Иней почти неподвижен, ему хватает 15 кадров в секунду; дождю и снегу нужно 30.
    val time = rememberEffectTime(if (glass.mode == GlassMode.FROST) 15 else 30)
    val appear = remember(glass.mode) { Animatable(0f) }
    LaunchedEffect(glass.mode) { appear.animateTo(1f, tween(1500)) }
    val wipes = remember { ArrayDeque<Wipe>() }
    val wipeData = remember { FloatArray(36) }
    val mode = when (glass.mode) {
        GlassMode.RAIN -> 1f
        GlassMode.SNOW -> 2f
        GlassMode.FROST -> 3f
        GlassMode.NONE -> 0f
    }
    return Modifier
        .pointerInput(glass.mode) {
            if (glass.mode != GlassMode.FROST) return@pointerInput
            // Иней оттаивает под пальцем: запоминаем, где вели, касания при этом работают как обычно.
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.filter { it.pressed }.forEach { change ->
                        val x = change.position.x / density
                        val y = change.position.y / density
                        val previous = wipes.lastOrNull()
                        if (previous == null || (previous.x - x) * (previous.x - x) + (previous.y - y) * (previous.y - y) > 100f) {
                            wipes.addLast(Wipe(x, y, time()))
                            while (wipes.size > 12) wipes.removeFirst()
                        }
                    }
                }
            }
        }
        .graphicsLayer {
            val now = time()
            try {
                for (i in 0 until 12) {
                    val w = wipes.getOrNull(i)
                    val age = if (w == null) 99f else now - w.at
                    wipeData[i * 3] = w?.x ?: 0f
                    wipeData[i * 3 + 1] = w?.y ?: 0f
                    wipeData[i * 3 + 2] = if (w == null) 0f else if (age < 1.5f) 1f else (1f - (age - 1.5f) / 3f).coerceAtLeast(0f)
                }
                shader.setFloatUniform("uSize", size.width, size.height)
                shader.setFloatUniform("uDp", density)
                shader.setFloatUniform("uTime", now)
                shader.setFloatUniform("uMode", mode)
                shader.setFloatUniform("uAmt", appear.value)
                shader.setFloatUniform("uStrength", glass.strength)
                shader.setFloatUniform("uWipe", wipeData)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            } catch (e: RuntimeException) {
                renderEffect = null
                failed = true
            }
        }
}

/** Живое небо: можно ли его рисовать на этом телефоне. */
object SkyFx {
    /** Шейдер неба не заработал на этом телефоне: дальше рисуется обычный фон. */
    var broken by mutableStateOf(false)

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !broken
}

/** Небо над Ижевском сейчас: пересчитывается раз в полминуты и когда приходит новый прогноз. */
@Composable
fun rememberSky(forecast: Forecast?): State<SkyState> =
    produceState(SkyState.at(LocalDateTime.now(), forecast), forecast) {
        while (true) {
            value = SkyState.at(LocalDateTime.now(), forecast)
            delay(30_000)
        }
    }

/**
 * Живое небо вместо обычного фона (Android 13+, тёмная тема). Считается в уменьшенном вдвое слое:
 * небо мягкое, а видеокарте в 4 раза меньше работы. Звёзды мерцают с частотой 20 кадров в секунду.
 * Небо читается только при рисовании, поэтому смена минут перерисовывает фон, не трогая экраны.
 */
@Composable
fun SkyBackground(sky: State<SkyState>, accent: Color, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    SkyLayer(sky, accent, EffectsSettings.animate(LocalContext.current), modifier)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun SkyLayer(sky: State<SkyState>, accent: Color, running: Boolean, modifier: Modifier) {
    val shader = remember { runCatching { RuntimeShader(SKY_AGSL) }.getOrNull() }
    if (shader == null) {
        SideEffect { SkyFx.broken = true }
        return
    }
    // При «меньше анимаций» небо остаётся, но звёзды не мерцают и картинка не перерисовывается.
    val time = rememberEffectTime(20, running)
    val scale = 2f
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .size(maxWidth / scale, maxHeight / scale)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            val now = sky.value
            try {
                shader.setFloatUniform("uSize", size.width, size.height)
                shader.setFloatUniform("uDp", density / scale)
                shader.setFloatUniform("uTime", time())
                shader.setFloatUniform("uHour", now.hour.toFloat())
                shader.setFloatUniform("uRise", now.sunrise.toFloat())
                shader.setFloatUniform("uSet", now.sunset.toFloat())
                shader.setFloatUniform("uMoon", now.moonPhase.toFloat())
                shader.setFloatUniform("uCloud", now.clouds.toFloat())
                shader.setFloatUniform("uAccent", accent.red, accent.green, accent.blue)
                drawRect(brush = ShaderBrush(shader))
            } catch (e: RuntimeException) {
                SkyFx.broken = true
            }
        }
    }
}
