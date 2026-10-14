package io.github.cmix7777.kazhdyidnevnik.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.DayItem
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.TogetherDay
import io.github.cmix7777.kazhdyidnevnik.data.shownEnd
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import io.github.cmix7777.kazhdyidnevnik.formatTime
import io.github.cmix7777.kazhdyidnevnik.formatWeekRange
import io.github.cmix7777.kazhdyidnevnik.ui.fx.EffectsSettings
import io.github.cmix7777.kazhdyidnevnik.ui.fx.GLUE_AGSL
import io.github.cmix7777.kazhdyidnevnik.ui.fx.Haptics
import io.github.cmix7777.kazhdyidnevnik.ui.fx.SkyFx
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberEffectTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Цвета склейки, выбранные в демо: у Айзата красный с оранжевым, у Насти голубой с фиолетовым. */
object TogetherColors {
    val aizatTop = Color(0xFFFF1F1F)
    val aizatBottom = Color(0xFFFF9A1F)
    val nastyaTop = Color(0xFF2196F3)
    val nastyaBottom = Color(0xFFB36BFF)

    fun top(person: Person) = if (person == Person.AIZAT) aizatTop else nastyaTop
    fun bottom(person: Person) = if (person == Person.AIZAT) aizatBottom else nastyaBottom
}

/** Параметры склейки, подобранные в демо. */
private object GlueParams {
    const val VISCOSITY_DP = 58f
    const val STIFFNESS = 150f
    const val DAMPING = 0.5f
    const val GLOW = 1f
}

private val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Неделя одного человека: дела по дням с понедельника. */
private data class PersonWeek(val person: Person, val days: List<List<DayItem>>)

/** Кнопка «вместе» на экране недели: два кружка цветов склейки. */
@Composable
fun TogetherChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(TogetherColors.aizatTop.copy(alpha = 0.22f), TogetherColors.nastyaTop.copy(alpha = 0.22f)),
                ),
            )
            .border(1.dp, Palette.BorderStrong, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 18.dp, height = 11.dp)) {
            val r = size.height / 2f
            drawCircle(TogetherColors.aizatTop, r, Offset(r, r))
            drawCircle(TogetherColors.nastyaTop.copy(alpha = 0.9f), r, Offset(size.width - r, r))
        }
        Text(text = "вместе", style = MaterialTheme.typography.labelMedium, color = Palette.Text)
    }
}

/** Положение двух недель в одном кадре склейки (в пикселях). */
private class GlueFrame(
    val ax: Float, val ay: Float, val bx: Float, val by: Float, val hw: Float, val hh: Float,
    val cx: Float, val cy: Float, val whw: Float, val whh: Float,
)

/**
 * Где стоят недели при степени склейки [at]: 0 — раздельно, 1 — слиты в одну карточку.
 * Больше 1 бывает при пружинном «желе»: карточки чуть вдавливаются друг в друга и вытягиваются по высоте.
 */
private class GlueGeometry(val width: Float, top: Float, bottom: Float, val dp: Float) {
    private val margin = 14f * dp
    private val hwMerged = (width - 2 * margin) / 4f
    val hwApart = hwMerged * 0.9f
    private val gapApart = width - 2 * margin - 4 * hwApart
    private val cy = (top + bottom) / 2f
    val hh0 = (bottom - top) / 2f - 18f * dp
    val radius = 24f * dp

    fun at(p: Float): GlueFrame {
        val pc = p.coerceIn(0f, 1f)
        val hw = hwApart + (hwMerged - hwApart) * pc
        val gap = gapApart * (1f - p)
        val squash = max(0f, p - 1f) * 0.6f
        val hh = hh0 * (0.94f + 0.06f * pc) + hh0 * squash * 0.25f
        val off = 18f * dp * (1f - p)
        val ax = width / 2f - gap / 2f - hw
        val bx = width / 2f + gap / 2f + hw
        val ay = cy - off
        val by = cy + off
        val left = ax - hw
        val right = bx + hw
        val topU = min(ay, by) - hh
        val bottomU = max(ay, by) + hh
        return GlueFrame(ax, ay, bx, by, hw, hh, (left + right) / 2f, (topU + bottomU) / 2f, (right - left) / 2f, (bottomU - topU) / 2f)
    }
}

private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Совместная неделя поверх экрана: две недели съезжаются, слипаются как жидкость и становятся одной.
 * Если потянуть общую неделю в сторону, перемычка рвётся и экран закрывается.
 */
@Composable
fun TogetherWeekOverlay(
    vm: ScheduleViewModel,
    diary: DiaryViewModel,
    offset: Int,
    onClose: () -> Unit,
    /** Насколько совместная неделя проявилась (0–1): страницы под ней гаснут на столько же. */
    onCover: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val now by rememberNow()
    val monday = weekStartFor(now.toLocalDate()).plusWeeks(offset.toLong())
    LaunchedEffect(offset) { Person.entries.forEach { vm.ensureWeek(it, offset) } }

    val weeks = Person.entries.map { person ->
        val lessons = vm.schedule(person).weeks[monday]?.lessons.orEmpty()
        val isOwner = person == vm.owner
        PersonWeek(person, (0..6).map { day -> diary.dayItems(person, isOwner, monday.plusDays(day.toLong()), lessons) })
    }

    val animate = remember { EffectsSettings.animate(context) }
    val p = remember { Animatable(if (animate) 0f else 1f) }
    val fuse = remember { Animatable(if (animate) 0f else 1f) }
    val shown = remember { Animatable(0f) }
    var latched by remember { mutableStateOf(!animate) }
    var closing by remember { mutableStateOf(false) }
    val glueSpec = spring<Float>(dampingRatio = GlueParams.DAMPING, stiffness = GlueParams.STIFFNESS)

    fun latch() {
        if (latched || closing) return
        latched = true
        Haptics.glue(context)
        scope.launch { fuse.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    }

    fun glue() {
        if (closing) return
        scope.launch {
            p.animateTo(1f, glueSpec) { if (value >= 0.985f) latch() }
            latch()
        }
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            // Даже если анимацию закрытия что-то прервёт, неделя всё равно закроется.
            try {
                latched = false
                fuse.animateTo(0f, tween(160))
                if (animate) {
                    Haptics.tear(context)
                    p.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 220f))
                }
                shown.animateTo(0f, tween(180))
            } finally {
                onClose()
            }
        }
    }

    LaunchedEffect(Unit) {
        shown.animateTo(1f, tween(200))
        if (animate) {
            delay(150)
            glue()
        }
    }
    // Переливы на склейке: 30 кадров в секунду хватает, чаще только зря грузит телефон.
    val clock = rememberEffectTime(fps = 30, running = animate)
    BackHandler { close() }
    val backdrop = LocalAccent.current.background
    // Страницы под совместной неделей гаснут вместе с её появлением: сверху виден только фон-небо.
    LaunchedEffect(Unit) { snapshotFlow { shown.value }.collect { onCover(it) } }
    DisposableEffect(Unit) { onDispose { onCover(0f) } }
    // Совместная неделя почти непрозрачна: небо под ней можно не перерисовывать.
    DisposableEffect(Unit) {
        SkyFx.covered = true
        onDispose { SkyFx.covered = false }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            // Сверху фон плавно проявляется из неба, без чёрной грани под переключателем имён;
            // ниже, где недели, он почти непрозрачный, чтобы ничего не просвечивало.
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to backdrop.copy(alpha = 0f),
                        0.45f to backdrop.copy(alpha = 0.7f),
                        1f to backdrop.copy(alpha = 0.97f),
                        startY = 0f,
                        endY = 150.dp.toPx(),
                    ),
                )
            }
            // Касания не уходят к страницам под совместной неделей.
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val dp = density.density
        val geometry = remember(widthPx, heightPx, dp) { GlueGeometry(widthPx, 132f * dp, heightPx - 12f * dp, dp) }
        val dark = LocalDark.current
        val shaderOk = remember { mutableStateOf(animate && EffectsSettings.shadersSupported) }

        if (shaderOk.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GlueShaderLayer(geometry, { p.value }, { fuse.value }, clock, dark, onFail = { shaderOk.value = false })
        } else {
            GlueSimpleLayer(geometry, { p.value }, { fuse.value })
        }

        // Шапка
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(text = "вместе", accent = true)
                Spacer(modifier = Modifier.weight(1f))
                GlassIconButton(icon = R.drawable.ic_arrow_back, contentDescription = "Закрыть совместную неделю", onClick = { close() })
            }
            TwoToneTitle(first = "Совместная неделя", second = formatWeekRange(monday), style = MaterialTheme.typography.headlineMedium)
        }

        // Две недели по отдельности
        val sepWidth = with(density) { (2f * geometry.hwApart - 18f * dp).toDp() }
        val sepHeight = with(density) { (2f * geometry.hh0 * 0.94f - 16f * dp).toDp() }
        weeks.forEachIndexed { index, week ->
            SeparateWeek(
                week = week,
                modifier = Modifier
                    .width(sepWidth)
                    .height(sepHeight)
                    .offset {
                        val f = geometry.at(p.value)
                        val x = if (index == 0) f.ax else f.bx
                        val y = if (index == 0) f.ay else f.by
                        IntOffset((x - sepWidth.toPx() / 2f).roundToInt(), (y - sepHeight.toPx() / 2f).roundToInt())
                    }
                    .graphicsLayer { alpha = (1f - smooth(0.62f, 0.97f, p.value)) * (1f - fuse.value) },
            )
        }

        // Общая неделя
        val mergedWidth = with(density) { (widthPx - 2f * 14f * dp - 26f * dp).toDp() }
        val mergedHeight = with(density) { (2f * geometry.hh0 - 20f * dp).toDp() }
        MergedWeek(
            weeks = weeks,
            modifier = Modifier
                .width(mergedWidth)
                .height(mergedHeight)
                .offset {
                    val f = geometry.at(p.value)
                    IntOffset((f.cx - mergedWidth.toPx() / 2f).roundToInt(), (f.cy - mergedHeight.toPx() / 2f).roundToInt())
                }
                .graphicsLayer { alpha = smooth(0.25f, 0.9f, fuse.value) }
                // Потянуть в сторону: перемычка растягивается и рвётся.
                .pointerInput(animate) {
                    if (!animate) return@pointerInput
                    var pulled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            pulled = 0f
                            if (closing) return@detectHorizontalDragGestures
                            if (latched) {
                                latched = false
                                scope.launch { fuse.animateTo(0f, tween(140)) }
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (closing) return@detectHorizontalDragGestures
                            pulled += amount
                            val value = (1f - abs(pulled) / (widthPx * 0.55f)).coerceIn(0f, 1f)
                            scope.launch { p.snapTo(value) }
                        },
                        onDragEnd = {
                            if (!closing) {
                                if (p.value < 0.55f) close() else glue()
                            }
                        },
                        onDragCancel = { glue() },
                    )
                },
        )
    }
}

/** Шейдерный слой склейки (Android 13+). Если шейдер не собрался, просим простой слой. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun GlueShaderLayer(
    geometry: GlueGeometry,
    p: () -> Float,
    fuse: () -> Float,
    time: () -> Float,
    dark: Boolean,
    onFail: () -> Unit,
) {
    val shader = remember { runCatching { RuntimeShader(GLUE_AGSL) }.getOrNull() }
    if (shader == null) {
        LaunchedEffect(Unit) { onFail() }
        return
    }
    val a1 = TogetherColors.aizatTop
    val a2 = TogetherColors.aizatBottom
    val b1 = TogetherColors.nastyaTop
    val b2 = TogetherColors.nastyaBottom
    Canvas(modifier = Modifier.fillMaxSize()) {
        try {
            val f = geometry.at(p())
            val dpPx = geometry.dp
            shader.setFloatUniform("uDpr", dpPx)
            shader.setFloatUniform("uTime", time())
            shader.setFloatUniform("uK", GlueParams.VISCOSITY_DP * dpPx)
            shader.setFloatUniform("uR", geometry.radius)
            shader.setFloatUniform("uFuse", fuse())
            shader.setFloatUniform("uGlow", GlueParams.GLOW)
            shader.setFloatUniform("uDark", if (dark) 1f else 0f)
            shader.setFloatUniform("uA", f.ax, f.ay, f.hw, f.hh)
            shader.setFloatUniform("uB", f.bx, f.by, f.hw, f.hh)
            shader.setFloatUniform("uW", f.cx, f.cy, f.whw, f.whh)
            shader.setFloatUniform("uA1", a1.red, a1.green, a1.blue)
            shader.setFloatUniform("uA2", a2.red, a2.green, a2.blue)
            shader.setFloatUniform("uB1", b1.red, b1.green, b1.blue)
            shader.setFloatUniform("uB2", b2.red, b2.green, b2.blue)
            drawRect(brush = ShaderBrush(shader))
        } catch (e: RuntimeException) {
            onFail()
        }
    }
}

/** Простой слой без шейдера: для старых телефонов и режима «меньше анимаций». */
@Composable
private fun GlueSimpleLayer(geometry: GlueGeometry, p: () -> Float, fuse: () -> Float) {
    val glassTop = Palette.CardTop
    val glassBottom = Palette.CardBottom
    Canvas(modifier = Modifier.fillMaxSize()) {
        val f = geometry.at(p())
        val merged = fuse()
        if (merged < 1f) {
            glassCard(f.ax, f.ay, f.hw, f.hh, geometry.radius, glassTop, glassBottom, TogetherColors.aizatTop, TogetherColors.aizatBottom, 1f - merged)
            glassCard(f.bx, f.by, f.hw, f.hh, geometry.radius, glassTop, glassBottom, TogetherColors.nastyaTop, TogetherColors.nastyaBottom, 1f - merged)
        }
        if (merged > 0f) {
            glassCard(
                f.cx, f.cy, f.whw, f.whh, geometry.radius, glassTop, glassBottom,
                lerp(TogetherColors.aizatTop, TogetherColors.nastyaTop, 0.5f),
                lerp(TogetherColors.aizatBottom, TogetherColors.nastyaBottom, 0.5f),
                merged,
            )
        }
    }
}

private fun DrawScope.glassCard(
    cx: Float, cy: Float, hw: Float, hh: Float, radius: Float,
    fillTop: Color, fillBottom: Color, edgeTop: Color, edgeBottom: Color, alpha: Float,
) {
    val topLeft = Offset(cx - hw, cy - hh)
    val size = Size(2f * hw, 2f * hh)
    val corner = CornerRadius(radius, radius)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(fillTop, fillBottom), startY = topLeft.y, endY = topLeft.y + size.height),
        topLeft = topLeft, size = size, cornerRadius = corner, alpha = alpha,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(edgeTop, edgeBottom), startY = topLeft.y, endY = topLeft.y + size.height),
        topLeft = topLeft, size = size, cornerRadius = corner, alpha = alpha, style = Stroke(width = 1.5f * density),
    )
}

/** Неделя одного человека в узкой карточке: когда занят и чем. */
@Composable
private fun SeparateWeek(week: PersonWeek, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TogetherColors.top(week.person)),
            )
            Text(text = week.person.shortName, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(6.dp))
        week.days.forEachIndexed { index, items ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = DAY_SHORT[index],
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.width(20.dp),
                )
                if (items.isEmpty()) {
                    Text(text = "свободно", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, maxLines = 1)
                } else {
                    Column {
                        Text(
                            text = "${formatTime(items.first().start)}–${formatTime(items.maxBy { it.end }.shownEnd)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Palette.Text,
                            maxLines = 1,
                        )
                        Text(
                            text = TogetherDay.kinds(items),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Общая неделя: по каждому дню две дорожки (Айзат и Настя) и светлая линия там, где свободны оба. */
@Composable
private fun MergedWeek(weeks: List<PersonWeek>, modifier: Modifier = Modifier) {
    val dayWidth = 26.dp
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            weeks.forEach { week ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TogetherColors.top(week.person)),
                    )
                    Text(text = week.person.shortName, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "8:00–23:00", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Spacer(modifier = Modifier.width(dayWidth))
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp),
            ) {
                val span = maxWidth
                listOf(8, 12, 16, 20).forEach { hour ->
                    val fraction = (hour * 60 - TogetherDay.START).toFloat() / (TogetherDay.END - TogetherDay.START)
                    Text(
                        text = "$hour:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint,
                        modifier = Modifier.offset(x = span * fraction - if (hour == 8) 0.dp else 12.dp),
                    )
                }
            }
        }
        for (day in 0..6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = DAY_SHORT[day],
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.width(dayWidth),
                )
                DayLanes(
                    a = weeks[0].days[day],
                    b = weeks[1].days[day],
                    colorA = weeks[0].person,
                    colorB = weeks[1].person,
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(Palette.Text.copy(alpha = 0.7f)),
            )
            Text(text = "оба свободны два часа и больше", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        }
    }
}

@Composable
private fun DayLanes(a: List<DayItem>, b: List<DayItem>, colorA: Person, colorB: Person, modifier: Modifier = Modifier) {
    val lane = Palette.Chip
    val freeColor = Palette.Text.copy(alpha = 0.55f)
    val free = remember(a, b) { TogetherDay.freeTogether(a, b) }
    val laneA = lerp(TogetherColors.top(colorA), TogetherColors.bottom(colorA), 0.3f)
    val laneB = lerp(TogetherColors.top(colorB), TogetherColors.bottom(colorB), 0.3f)
    Canvas(modifier = modifier) {
        val laneHeight = 6.dp.toPx()
        val round = CornerRadius(laneHeight / 2f, laneHeight / 2f)
        val y1 = 3.dp.toPx()
        val y2 = y1 + laneHeight + 3.dp.toPx()
        val y3 = y2 + laneHeight + 4.dp.toPx()
        fun x(minute: Int): Float =
            size.width * ((minute - TogetherDay.START).toFloat() / (TogetherDay.END - TogetherDay.START)).coerceIn(0f, 1f)
        drawRoundRect(lane, Offset(0f, y1), Size(size.width, laneHeight), round)
        drawRoundRect(lane, Offset(0f, y2), Size(size.width, laneHeight), round)
        for ((items, color, y) in listOf(Triple(a, laneA, y1), Triple(b, laneB, y2))) {
            items.forEach { item ->
                val (from, to) = TogetherDay.minutes(item)
                val left = x(from)
                val right = x(to)
                if (right - left > 0.5f) drawRoundRect(color, Offset(left, y), Size(right - left, laneHeight), round)
            }
        }
        free.forEach { (from, to) ->
            val thin = 2.dp.toPx()
            drawRoundRect(freeColor, Offset(x(from), y3), Size(x(to) - x(from), thin), CornerRadius(thin / 2f, thin / 2f))
        }
    }
}
