package io.github.cmix7777.kazhdyidnevnik.ui

import android.content.Context
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.WallpaperChoice
import io.github.cmix7777.kazhdyidnevnik.data.WallpaperInfo
import io.github.cmix7777.kazhdyidnevnik.data.Wallpapers
import io.github.cmix7777.kazhdyidnevnik.ui.fx.EffectsSettings
import io.github.cmix7777.kazhdyidnevnik.ui.fx.wallpaperSource
import kotlinx.coroutines.delay
import kotlin.math.ceil

/*
 * Живой фон переписки: шестнадцать сцен на выбор, у каждой можно повернуть оттенок, прибавить яркость
 * и скорость. Фон отзывается на наклон телефона, на касания (расходится круг) и на новые сообщения (волна).
 * Считается он в уменьшенном размере отдельным слоем и растягивается; если телефон всё равно не успевает,
 * сам становится грубее. На телефонах до Android 13 и при «меньше анимаций» остаётся прежняя дымка.
 */

/** Выбранный фон: хранится на телефоне рядом с масштабом переписки. */
object WallpaperStore {
    var choice by mutableStateOf(WallpaperChoice())
        private set
    private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("chat-ui", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        choice = Wallpapers.clean(
            WallpaperChoice(
                id = p.getString("wall-id", null) ?: Wallpapers.SMOKE,
                hue = p.getFloat("wall-hue", 0f),
                power = p.getFloat("wall-power", 1f),
                speed = p.getFloat("wall-speed", 1f),
                tilt = p.getBoolean("wall-tilt", true),
                touch = p.getBoolean("wall-touch", true),
            ),
        )
    }

    /** Новый выбор. Пока двигают ползунок, [persist] выключен: на телефон пишется, когда палец отпущен. */
    fun set(context: Context, value: WallpaperChoice, persist: Boolean = true) {
        loaded = true
        val clean = Wallpapers.clean(value)
        choice = clean
        if (!persist) return
        prefs(context).edit()
            .putString("wall-id", clean.id)
            .putFloat("wall-hue", clean.hue)
            .putFloat("wall-power", clean.power)
            .putFloat("wall-speed", clean.speed)
            .putBoolean("wall-tilt", clean.tilt)
            .putBoolean("wall-touch", clean.touch)
            .apply()
    }

    /** Для тестов: забыть выбранное, как после установки. */
    fun resetForTest(context: Context) {
        prefs(context).edit()
            .remove("wall-id").remove("wall-hue").remove("wall-power").remove("wall-speed").remove("wall-tilt").remove("wall-touch")
            .commit()
        loaded = false
        choice = WallpaperChoice()
    }
}

/**
 * То, на что фон отзывается: последнее касание, новое сообщение и наклон телефона.
 * Это обычные поля, а не состояние Compose: их читают при рисовании очередного кадра, и экран из-за них не пересобирается.
 */
@Stable
internal class WallpaperLive {
    var touchX = 0.5f
    var touchY = 0.5f
    var touchAt = 0L
    var pulseAt = 0L
    var tiltX = 0f
    var tiltY = 0f

    /** Касание в долях экрана (0..1). */
    fun touch(x: Float, y: Float) {
        touchX = x.coerceIn(0f, 1f)
        touchY = y.coerceIn(0f, 1f)
        touchAt = SystemClock.uptimeMillis()
    }

    /** Пришло или ушло сообщение: по фону пройдёт волна. */
    fun pulse() {
        pulseAt = SystemClock.uptimeMillis()
    }
}

/** Ловит касания для кругов на фоне, ничего не забирая у переписки. */
internal fun Modifier.wallpaperTouches(live: WallpaperLive, enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(live) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (size.width > 0 && size.height > 0) {
                    live.touch(down.position.x / size.width, down.position.y / size.height)
                }
            }
        }
    }

/**
 * Наклон телефона для фона. Середина — то, как телефон держат сейчас (она медленно подстраивается),
 * поэтому фон сдвигается от движения руки и сам возвращается на место. Датчик слушается, только пока экран на виду.
 */
@Composable
internal fun WallpaperTilt(live: WallpaperLive, enabled: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(enabled, owner) {
        val manager = if (enabled) context.getSystemService(SensorManager::class.java) else null
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            live.tiltX = 0f
            live.tiltY = 0f
            return@DisposableEffect onDispose { }
        }
        var baseX = 0f
        var baseY = 0f
        var primed = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 2) return
                val x = event.values[0] / 9.81f
                val y = event.values[1] / 9.81f
                if (!x.isFinite() || !y.isFinite()) return
                if (!primed) {
                    baseX = x
                    baseY = y
                    primed = true
                }
                baseX += (x - baseX) * 0.012f
                baseY += (y - baseY) * 0.012f
                val tx = ((baseX - x) * 3.5f).coerceIn(-1f, 1f)
                val ty = ((y - baseY) * 3.5f).coerceIn(-1f, 1f)
                live.tiltX += (tx - live.tiltX) * 0.18f
                live.tiltY += (ty - live.tiltY) * 0.18f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !registered) {
                registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            } else if (event == Lifecycle.Event.ON_PAUSE && registered) {
                manager.unregisterListener(listener)
                registered = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (registered) manager.unregisterListener(listener)
            live.tiltX = 0f
            live.tiltY = 0f
        }
    }
}

/** На каком уровне качества фон идёт без рывков на этом телефоне (пока приложение не закрыли). */
private val qualityLevels = HashMap<String, Int>()

/**
 * Часы фона: тикают [fps] раз в секунду со скоростью [speed]. Если кадры приходят заметно реже, чем просили
 * (телефон не успевает), зовут [onSlow]. Первые две секунды не в счёт: экран ещё открывается.
 */
@Composable
private fun rememberWallpaperClock(fps: Int, running: Boolean, speed: Float, onSlow: (() -> Unit)?): () -> Float {
    var time by remember { mutableFloatStateOf(0f) }
    val speedNow = rememberUpdatedState(speed)
    val slowNow = rememberUpdatedState(onSlow)
    LaunchedEffect(fps, running) {
        if (!running || fps <= 0) return@LaunchedEffect
        val stepMs = 1000L / fps
        var last = withInfiniteAnimationFrameNanos { it }
        var warm = fps * 2
        var ticks = 0
        var late = 0
        while (true) {
            delay((stepMs - 6L).coerceAtLeast(0L))
            val now = withInfiniteAnimationFrameNanos { it }
            val passed = (now - last) / 1_000_000_000f
            last = now
            time = (time + passed.coerceIn(0f, 0.1f) * speedNow.value) % 3600f
            if (warm > 0) {
                warm--
            } else {
                ticks++
                if (passed * 1000f > stepMs * 1.7f + 8f) late++
                if (ticks >= 90) {
                    if (late >= 30) slowNow.value?.invoke()
                    ticks = 0
                    late = 0
                }
            }
        }
    }
    return { time }
}

/**
 * Фон переписки по выбору [choice]. [running] = false — картинка стоит («меньше анимаций»).
 * [live] — касания, волна и наклон; в маленьких плитках выбора его нет ([tile]).
 * [clock] подменяет часы в тестах.
 */
@Composable
internal fun ChatWallpaper(
    choice: WallpaperChoice,
    owner: Person,
    running: Boolean,
    live: WallpaperLive?,
    modifier: Modifier = Modifier,
    tile: Boolean = false,
    clock: (() -> Float)? = null,
) {
    val dark = LocalDark.current
    val accent = Accents.of(owner, dark)
    if (choice.id == Wallpapers.NONE) return
    if (!Wallpapers.live(choice.id) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        ChatSmoke(
            mine = accent.primary,
            theirs = Accents.of(owner.partner, dark).primary,
            background = accent.background,
            running = running,
            modifier = modifier,
        )
        return
    }
    WallpaperLayer(
        info = Wallpapers.info(choice.id),
        choice = choice,
        background = accent.background,
        ink = if (dark) accent.primary else lerp(accent.primary, Color.White, 0.3f),
        dark = dark,
        running = running,
        live = live,
        tile = tile,
        clock = clock,
        modifier = modifier,
    )
}

/** Почему фон не собрался на этом телефоне (null — всё в порядке). Нужно тестам. */
internal fun wallpaperCompileError(id: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val source = wallpaperSource(id) ?: return "нет такого фона"
    return try {
        RuntimeShader(source)
        null
    } catch (e: RuntimeException) {
        e.message ?: e.toString()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun WallpaperLayer(
    info: WallpaperInfo,
    choice: WallpaperChoice,
    background: Color,
    ink: Color,
    dark: Boolean,
    running: Boolean,
    live: WallpaperLive?,
    tile: Boolean,
    clock: (() -> Float)?,
    modifier: Modifier,
) {
    val shader = remember(info.id) { wallpaperSource(info.id)?.let { source -> runCatching { RuntimeShader(source) }.getOrNull() } } ?: return
    var level by remember(info.id) { mutableIntStateOf(if (tile) 0 else qualityLevels[info.id] ?: 0) }
    val (baseScale, baseFps) = Wallpapers.quality(info, level)
    // Плиткам выбора хватает десяти кадров в секунду: их на экране сразу несколько.
    val fps = if (tile) minOf(baseFps, 10) else baseFps
    val auto = rememberWallpaperClock(
        fps = fps,
        running = running && clock == null,
        speed = choice.speed,
        onSlow = if (tile) {
            null
        } else {
            {
                if (level < Wallpapers.MAX_LEVEL) {
                    level++
                    qualityLevels[info.id] = level
                }
            }
        },
    )
    val time = clock ?: auto
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        if (widthPx <= 0 || heightPx <= 0 || widthPx == Constraints.Infinity || heightPx == Constraints.Infinity) return@BoxWithConstraints
        val layerWidth = ceil(widthPx / baseScale).toInt().coerceAtLeast(1)
        val layerHeight = ceil(heightPx / baseScale).toInt().coerceAtLeast(1)
        Canvas(
            modifier = Modifier
                .size(with(density) { layerWidth.toDp() }, with(density) { layerHeight.toDp() })
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = widthPx / layerWidth.toFloat()
                    scaleY = heightPx / layerHeight.toFloat()
                    // Отдельным слоем: фон считается в уменьшенном размере и растягивается готовой картинкой.
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            try {
                val now = SystemClock.uptimeMillis()
                shader.setFloatUniform("uSize", size.width, size.height)
                // Пока время стоит («меньше анимаций»), фон замер на красивом месте, а не в самом начале.
                shader.setFloatUniform("uTime", time() + 40f)
                shader.setFloatUniform("uDark", if (dark) 1f else 0f)
                shader.setFloatUniform("uBg", background.red, background.green, background.blue)
                shader.setFloatUniform("uInk", ink.red, ink.green, ink.blue)
                shader.setFloatUniform("uHue", choice.hue * 6.2831855f)
                shader.setFloatUniform("uPower", choice.power)
                val touchAge = if (live != null && choice.touch && live.touchAt > 0L) (now - live.touchAt) / 1000f else -1f
                val pulseAge = if (live != null && choice.touch && live.pulseAt > 0L) (now - live.pulseAt) / 1000f else -1f
                if (live != null && choice.tilt) {
                    shader.setFloatUniform("uTilt", live.tiltX, live.tiltY)
                } else {
                    shader.setFloatUniform("uTilt", 0f, 0f)
                }
                if (live != null && touchAge >= 0f && touchAge < 1.8f) {
                    shader.setFloatUniform("uTouch", live.touchX, live.touchY, touchAge)
                } else {
                    shader.setFloatUniform("uTouch", 0f, 0f, -1f)
                }
                shader.setFloatUniform("uPulse", if (pulseAge >= 0f && pulseAge < 2.2f) pulseAge else -1f)
                drawRect(brush = ShaderBrush(shader))
            } catch (e: RuntimeException) {
                // Шейдер не заработал на этом телефоне: остаётся обычный фон.
            }
        }
    }
}

// ---------- окно выбора ----------

/**
 * Выбор фона: сам фон идёт во весь экран за образцом переписки, внизу — плитки с живыми превью и настройки.
 * [onChange] зовётся на каждое изменение; второй параметр — пора ли записать выбор на телефон.
 */
@Composable
internal fun WallpaperDialog(
    choice: WallpaperChoice,
    owner: Person,
    onChange: (WallpaperChoice, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val dark = LocalDark.current
    val accent = Accents.of(owner, dark)
    val animate = EffectsSettings.animate(context)
    val shaders = EffectsSettings.shadersSupported
    val isLive = shaders && Wallpapers.live(choice.id)
    val live = remember { WallpaperLive() }
    val latest = rememberUpdatedState(choice)
    val list = remember(shaders) { if (shaders) Wallpapers.ALL else Wallpapers.ALL.filter { !Wallpapers.live(it.id) } }
    WallpaperTilt(live, enabled = isLive && choice.tilt && animate)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(accent.background)
                .wallpaperTouches(live, isLive && choice.touch),
        ) {
            ChatWallpaper(choice = choice, owner = owner, running = animate, live = live, modifier = Modifier.matchParentSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassIconButton(icon = R.drawable.ic_arrow_back, contentDescription = "Назад", onClick = onClose)
                    Text("Фон переписки", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                }
                // Образец переписки: видно, как сообщения читаются на этом фоне.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    SampleBubble("Смотри, какой фон", mine = false, owner = owner)
                    SampleBubble("Красота. Потрогай его и наклони телефон", mine = true, owner = owner)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Palette.Background.copy(alpha = 0.94f))
                        .padding(top = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("wall-list"),
                    ) {
                        items(list, key = { it.id }) { info ->
                            WallpaperTile(
                                info = info,
                                choice = choice,
                                owner = owner,
                                selected = info.id == choice.id,
                                animate = animate,
                                onClick = { onChange(latest.value.copy(id = info.id), true) },
                            )
                        }
                    }
                    if (isLive) {
                        WallSlider("Оттенок", choice.hue, 0f..0.999f, "wall-hue", accent.primary, { onChange(latest.value.copy(hue = it), false) }) {
                            onChange(latest.value, true)
                        }
                        WallSlider("Яркость", choice.power, Wallpapers.POWER_MIN..Wallpapers.POWER_MAX, "wall-power", accent.primary, { onChange(latest.value.copy(power = it), false) }) {
                            onChange(latest.value, true)
                        }
                        WallSlider("Скорость", choice.speed, Wallpapers.SPEED_MIN..Wallpapers.SPEED_MAX, "wall-speed", accent.primary, { onChange(latest.value.copy(speed = it), false) }) {
                            onChange(latest.value, true)
                        }
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SelectPill(
                                text = "Наклон",
                                selected = choice.tilt,
                                onClick = { onChange(latest.value.copy(tilt = !latest.value.tilt), true) },
                                modifier = Modifier.testTag("wall-tilt"),
                            )
                            SelectPill(
                                text = "Касания",
                                selected = choice.touch,
                                onClick = { onChange(latest.value.copy(touch = !latest.value.touch), true) },
                                modifier = Modifier.testTag("wall-touch"),
                            )
                            SelectPill(
                                text = "Как было",
                                selected = false,
                                onClick = { onChange(latest.value.copy(hue = 0f, power = 1f, speed = 1f), true) },
                                modifier = Modifier.testTag("wall-reset"),
                            )
                        }
                    }
                    val note = when {
                        !shaders -> "Живые фоны работают с Android 13. На этом телефоне — дымка или ничего."
                        isLive && !animate -> "Включено «меньше анимаций»: фон стоит на месте."
                        isLive -> "Фон видишь только ты. Наклон — фон сдвигается за движением телефона, касания — круги под пальцем и волна от новых сообщений."
                        else -> "Фон видишь только ты: у второго телефона свой."
                    }
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Text(
                        text = "Готово",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(accent.primary, accent.deep)))
                            .clickable(onClick = onClose)
                            .padding(vertical = 12.dp)
                            .testTag("wall-done"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleBubble(text: String, mine: Boolean, owner: Person) {
    val dark = LocalDark.current
    val me = Accents.of(owner, dark)
    val other = Accents.of(owner.partner, dark)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (mine) Color.White else Palette.Text,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (mine) {
                        Brush.linearGradient(listOf(me.primary, me.deep))
                    } else {
                        Brush.verticalGradient(listOf(other.cardTop.copy(alpha = 0.96f), other.cardBottom.copy(alpha = 0.94f)))
                    },
                )
                .padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun WallpaperTile(
    info: WallpaperInfo,
    choice: WallpaperChoice,
    owner: Person,
    selected: Boolean,
    animate: Boolean,
    onClick: () -> Unit,
) {
    val accent = Accents.of(owner, LocalDark.current)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(78.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag("wall-${info.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(78.dp, 124.dp)
                .clip(shape)
                .background(accent.background)
                .border(if (selected) 2.dp else 1.dp, if (selected) accent.primary else Palette.BorderStrong, shape),
        ) {
            ChatWallpaper(
                choice = choice.copy(id = info.id, tilt = false, touch = false),
                owner = owner,
                running = animate,
                live = null,
                modifier = Modifier.matchParentSize(),
                tile = true,
            )
        }
        Text(
            text = info.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Palette.Text else Palette.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(30.dp),
        )
    }
}

@Composable
private fun WallSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    accent: Color,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, modifier = Modifier.width(78.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Palette.Chip),
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .testTag(tag),
        )
    }
}
