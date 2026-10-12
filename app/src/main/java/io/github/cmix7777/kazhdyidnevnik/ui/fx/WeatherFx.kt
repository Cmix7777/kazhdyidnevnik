package io.github.cmix7777.kazhdyidnevnik.ui.fx

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
 * Время для бесконечных эффектов, примерно [fps] раз в секунду. Между кадрами корутина спит, а не
 * просыпается на каждом кадре экрана. Время копится по кадрам (не больше 0,1 с за раз), поэтому
 * после сворачивания приложения на несколько часов оно не улетает в огромные числа, а через час
 * начинается заново. Если [running] выключен («меньше анимаций», страница не видна), время стоит
 * и картинка не перерисовывается. В автотестах с автоматическими часами такие анимации тоже стоят.
 */
@Composable
internal fun rememberEffectTime(fps: Int, running: Boolean = true): () -> Float {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fps, running) {
        if (!running || fps <= 0) return@LaunchedEffect
        val stepMs = 1000L / fps
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            // Просыпаемся чуть раньше нужного и берём ближайший кадр экрана: шаг ровный, без рывков.
            delay((stepMs - 6L).coerceAtLeast(0L))
            val now = withInfiniteAnimationFrameNanos { it }
            val passed = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
            time = (time + passed) % 3600f
        }
    }
    return { time }
}

/**
 * Погода на стекле поверх содержимого (Android 13+): дождь, снег или мороз по прогнозу.
 * [clock] — общие часы страницы (их же использует полоска дня, чтобы стекло перерисовывалось
 * один раз за тик, а не дважды). [active] = false — страница не на экране, эффект выключен.
 * Если погода обычная, телефон старый или включено «меньше анимаций» — ничего не меняет.
 */
@Composable
fun Modifier.weatherGlass(glass: GlassWeather, clock: () -> Float, active: Boolean = true): Modifier {
    val context = LocalContext.current
    if (!active || glass.mode == GlassMode.NONE || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !EffectsSettings.animate(context)) {
        return this
    }
    return this.then(weatherGlassLayer(glass, clock))
}

/** Нужны ли стеклу часы: дождь и снег движутся, иней стоит (оттаивание считается отдельно). */
fun GlassWeather.moving(): Boolean = mode == GlassMode.RAIN || mode == GlassMode.SNOW

private class Wipe(val x: Float, val y: Float, val at: Long)

/** Сколько живёт след пальца на инее: секунда полностью, потом три секунды затягивается. */
private const val WIPE_HOLD_MS = 1_500L
private const val WIPE_FADE_MS = 3_000L

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun weatherGlassLayer(glass: GlassWeather, clock: () -> Float): Modifier {
    val shader = remember { runCatching { RuntimeShader(WEATHER_GLASS_AGSL) }.getOrNull() }
    var failed by remember { mutableStateOf(false) }
    if (shader == null || failed) return Modifier
    val density = LocalDensity.current.density
    val appear = remember(glass.mode) { Animatable(0f) }
    LaunchedEffect(glass.mode) { appear.animateTo(1f, tween(1500)) }
    val wipes = remember { ArrayDeque<Wipe>() }
    val wipeData = remember { FloatArray(36) }
    // Иней перерисовывается только пока под пальцем оттаивает и снова затягивается.
    var lastWipe by remember { mutableLongStateOf(0L) }
    var wipeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lastWipe) {
        if (lastWipe == 0L) return@LaunchedEffect
        while (SystemClock.uptimeMillis() - lastWipe < WIPE_HOLD_MS + WIPE_FADE_MS + 100L) {
            delay(66)
            wipeTick++
        }
    }
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
                            val now = SystemClock.uptimeMillis()
                            wipes.addLast(Wipe(x, y, now))
                            while (wipes.size > 12) wipes.removeFirst()
                            lastWipe = now
                        }
                    }
                }
            }
        }
        .graphicsLayer {
            val time = clock()
            @Suppress("UNUSED_VARIABLE")
            val tick = wipeTick
            val now = SystemClock.uptimeMillis()
            try {
                for (i in 0 until 12) {
                    val w = wipes.getOrNull(i)
                    val age = if (w == null) Long.MAX_VALUE else now - w.at
                    wipeData[i * 3] = w?.x ?: 0f
                    wipeData[i * 3 + 1] = w?.y ?: 0f
                    wipeData[i * 3 + 2] = when {
                        w == null -> 0f
                        age < WIPE_HOLD_MS -> 1f
                        else -> (1f - (age - WIPE_HOLD_MS).toFloat() / WIPE_FADE_MS).coerceAtLeast(0f)
                    }
                }
                shader.setFloatUniform("uSize", size.width, size.height)
                shader.setFloatUniform("uDp", density)
                shader.setFloatUniform("uTime", time)
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

    /** Небо закрыто чем-то почти непрозрачным (совместная неделя): перерисовывать его незачем. */
    var covered by mutableStateOf(false)

    /**
     * Как часто перерисовывать небо. Ночью мерцают звёзды и пролетают падающие — 20 раз в секунду.
     * Днём и в сумерках двигаются только облака, им хватает 4 раз. Ясным днём небо стоит.
     */
    fun fps(sky: SkyState): Int {
        val night = sky.hour < sky.sunrise - 0.6 || sky.hour > sky.sunset + 0.6
        return when {
            night && sky.clouds < 0.9 -> 20
            sky.clouds > 0.03 -> 4
            else -> 0
        }
    }
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
    val time = rememberEffectTime(SkyFx.fps(sky.value), running && !SkyFx.covered)
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
