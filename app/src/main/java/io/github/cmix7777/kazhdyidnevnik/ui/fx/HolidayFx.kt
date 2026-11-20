package io.github.cmix7777.kazhdyidnevnik.ui.fx

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cmix7777.kazhdyidnevnik.data.pluralDays
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Праздники, которые сами приходят в приложение на свои даты и сами уходят. */
object Holidays {

    enum class Kind { NEW_YEAR }

    data class Active(val kind: Kind, val newYear: Int, val start: LocalDate, val end: LocalDate)

    /** Новый год: с 15 декабря по 14 января. [preview] — показать заранее для проверки. */
    fun active(date: LocalDate, preview: Boolean = false): Active? {
        val year = if (date.monthValue == 1) date.year - 1 else date.year
        val start = LocalDate.of(year, 12, 15)
        val end = LocalDate.of(year + 1, 1, 14)
        val inside = !date.isBefore(start) && !date.isAfter(end)
        return if (inside || preview) Active(Kind.NEW_YEAR, year + 1, start, end) else null
    }

    fun daysLeft(date: LocalDate, active: Active): Long? {
        val day = LocalDate.of(active.newYear, 1, 1)
        return if (date.isBefore(day)) ChronoUnit.DAYS.between(date, day) else null
    }

    fun current(context: Context, date: LocalDate = LocalDate.now()): Active? = active(date, AppSettings(context).holidayPreview)

    /** День, когда кто-то писал в переписке или отмечал привычку: на ёлке появляется игрушка. */
    fun markDay(context: Context, date: LocalDate = LocalDate.now()) {
        val active = current(context, date) ?: return
        val settings = AppSettings(context)
        val key = "${active.newYear}:$date"
        if (key !in settings.holidayDays) settings.holidayDays = settings.holidayDays + key
    }

    fun ornaments(context: Context, active: Active): Int =
        AppSettings(context).holidayDays.count { it.startsWith("${active.newYear}:") }
}

private val Bulbs = listOf(
    Color(0xFFFFC46B), Color(0xFFFF6F61), Color(0xFFFFE7B0),
    Color(0xFFB79CFF), Color(0xFFFF9E6B), Color(0xFF7FE0C2),
)

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.01f || radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent), center, radius),
        radius = radius,
        center = center,
        blendMode = BlendMode.Plus,
    )
}

/** Праздник на сегодня: пересчитывается раз в минуту, чтобы прийти и уйти самому. */
@Composable
fun rememberHoliday(): Holidays.Active? {
    val context = LocalContext.current
    val today by produceState(LocalDate.now()) {
        while (true) {
            delay(60_000)
            value = LocalDate.now()
        }
    }
    return Holidays.active(today, AppSettings(context).holidayPreview)
}

/** Приход праздника: один раз гирлянда опускается сверху и огоньки загораются по очереди. */
@Composable
fun rememberHolidayIntro(active: Holidays.Active?): Float {
    val context = LocalContext.current
    val key = active?.let { "intro-${it.newYear}" }
    val shown = remember(key) { key == null || AppSettings(context).holidayIntro == key || !EffectsSettings.animate(context) }
    val progress = remember(key) { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(key) {
        if (key == null || shown) return@LaunchedEffect
        delay(400)
        progress.animateTo(1f, tween(2600, easing = FastOutSlowInEasing))
        AppSettings(context).holidayIntro = key
    }
    return progress.value
}

/** Гирлянда на крючках вдоль верхнего края: огоньки мерцают бегущей волной. */
@Composable
fun HolidayGarland(modifier: Modifier = Modifier, intro: Float = 1f, running: Boolean = true) {
    val animate = running && EffectsSettings.animate(LocalContext.current)
    val time = rememberEffectTime(fps = 24, running = animate)
    Canvas(modifier) {
        val t = time()
        val span = 74.dp.toPx()
        val sag = 18.dp.toPx()
        val drop = (1f - intro) * 46.dp.toPx()
        val wire = Path()
        val points = ArrayList<Offset>()
        val colors = ArrayList<Color>()
        val phases = ArrayList<Float>()
        var x0 = -10f
        var segment = 0
        var first = true
        while (x0 < size.width + 10f) {
            val x1 = x0 + span
            for (k in 0..12) {
                val f = k / 12f
                val x = x0 + (x1 - x0) * f
                val y = sin(f * PI.toFloat()) * sag - drop
                if (first) wire.moveTo(x, y) else wire.lineTo(x, y)
                first = false
            }
            for (k in 1..3) {
                val f = k / 4f
                points += Offset(x0 + (x1 - x0) * f, sin(f * PI.toFloat()) * sag - drop + 5.dp.toPx())
                colors += Bulbs[(segment * 3 + k) % Bulbs.size]
                phases += (segment * 3 + k) * 0.6f
            }
            x0 = x1
            segment++
        }
        drawPath(wire, Color(0xFF8E7C62), style = Stroke(1.dp.toPx()))
        val total = points.size
        for (i in points.indices) {
            val p = points[i]
            val c = colors[i]
            val lit = (intro * (total + 4) - i).coerceIn(0f, 1f)
            val wave = 0.5f + 0.5f * sin(t * 2.2f - phases[i])
            val a = (0.72f + 0.28f * wave) * lit
            drawRect(Color(0xFF4A3D36), topLeft = Offset(p.x - 2.5.dp.toPx(), p.y - 6.dp.toPx()), size = Size(5.dp.toPx(), 4.dp.toPx()))
            drawOval(
                brush = Brush.radialGradient(listOf(Color.White, c, c), Offset(p.x - 1.dp.toPx(), p.y), 6.dp.toPx()),
                topLeft = Offset(p.x - 4.dp.toPx(), p.y - 4.dp.toPx()),
                size = Size(8.dp.toPx(), 11.dp.toPx()),
                alpha = 0.35f + 0.65f * a,
            )
            glow(c, Offset(p.x, p.y + 1.5.dp.toPx()), 15.dp.toPx(), a * 0.38f)
        }
    }
}

private class Flake(val x: Float, val y: Float, val z: Float, val r: Float, val v: Float, val ph: Float)

/** Снег поверх экрана: три слоя глубины, ближние хлопья крупные и мягкие. */
@Composable
fun HolidaySnow(
    modifier: Modifier = Modifier,
    count: Int = 70,
    alpha: Float = 1f,
    running: Boolean = true,
) {
    val animate = running && EffectsSettings.animate(LocalContext.current)
    val time = rememberEffectTime(fps = 30, running = animate)
    val flakes = remember(count) {
        val rnd = java.util.Random(2027)
        List(count) {
            val z = rnd.nextFloat()
            val r = when {
                z < 0.6f -> 0.6f + z * 1.2f
                z < 0.92f -> 1.2f + z * 1.4f
                else -> 2.4f + rnd.nextFloat() * 2f
            }
            Flake(rnd.nextFloat(), rnd.nextFloat(), z, r, 16f + z * 50f, rnd.nextFloat() * 6f)
        }
    }
    Canvas(modifier) {
        val t = time()
        val dp = 1.dp.toPx()
        val w = size.width
        val h = size.height + 20f
        for (f in flakes) {
            val y = (f.y * h + f.v * dp * t) % h - 10f
            val x = ((f.x * w + sin(t * 0.6f + f.ph) * 8f * dp + sin(t * 0.13f) * 14f * dp * f.z) % w + w) % w
            if (f.z >= 0.92f) {
                glow(Color(0xFFF5F8FF), Offset(x, y), f.r * 2f * dp, 0.45f * alpha)
            } else {
                drawCircle(Color(0xFFF4F7FF), radius = f.r * dp, center = Offset(x, y), alpha = (0.3f + f.z * 0.55f) * alpha)
            }
        }
    }
}

@Composable
fun HolidayCard(
    daysLeft: Long?,
    ornaments: Int,
    intro: Float,
    running: Boolean,
    onPreviewSalute: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(22.dp)
    val gold = Color(0xFFF5D593)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF153629), Color(0xFF10221E))))
            .border(1.dp, gold.copy(alpha = 0.25f), shape),
    ) {
        HolidaySnow(Modifier.matchParentSize(), count = 28, alpha = 0.57f, running = running)
        HolidayGarland(
            modifier = Modifier.fillMaxWidth().height(38.dp),
            intro = intro,
            running = running,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 38.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (daysLeft != null) {
                    Text("До Нового года", color = gold, style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = daysLeft?.let(::pluralDays) ?: "С Новым годом!",
                    color = Color(0xFFFFFAEF),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Box(Modifier.size(width = 108.dp, height = 110.dp)) {
                HolidayTree(
                    ornaments = ornaments,
                    modifier = Modifier.align(Alignment.TopCenter).size(width = 86.dp, height = 108.dp),
                    running = running,
                    onLongPress = onPreviewSalute,
                )
                HolidayGifts(Modifier.align(Alignment.BottomCenter).size(width = 108.dp, height = 34.dp))
            }
        }
    }
}

@Composable
private fun HolidayGifts(modifier: Modifier) {
    Canvas(modifier) {
        drawGift(8.dp.toPx(), 8.dp.toPx(), 32.dp.toPx(), 19.dp.toPx(), Color(0xFFB44D48), Color(0xFFFFD58E))
        drawGift(71.dp.toPx(), 8.dp.toPx(), 29.dp.toPx(), 22.dp.toPx(), Color(0xFFD19B52), Color(0xFFFFE7B5))
        drawGift(36.dp.toPx(), 15.dp.toPx(), 25.dp.toPx(), 18.dp.toPx(), Color(0xFF4D8594), Color(0xFFC0E7DF))
    }
}

private fun DrawScope.drawGift(x: Float, y: Float, width: Float, height: Float, paper: Color, ribbon: Color) {
    val lid = 5.dp.toPx()
    val band = 4.dp.toPx()
    val bow = 7.dp.toPx()
    val center = x + width / 2
    val loops = Path().apply {
        moveTo(center, y + 1.dp.toPx())
        cubicTo(center - bow * 2, y - bow / 2, center - bow, y - bow * 1.7f, center, y + 1.dp.toPx())
        cubicTo(center + bow, y - bow * 1.7f, center + bow * 2, y - bow / 2, center, y + 1.dp.toPx())
    }
    drawPath(loops, ribbon, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    drawRect(paper, Offset(x, y), Size(width, height))
    drawRect(Color.White.copy(alpha = 0.15f), Offset(x, y), Size(width, lid))
    drawRect(ribbon, Offset(center - band / 2, y), Size(band, height))
}

private val OrnamentColors = listOf(
    Color(0xFFE5483C) to Color(0xFF5A0F0B),
    Color(0xFFE9C77E) to Color(0xFF5E4415),
    Color(0xFF9B6BFF) to Color(0xFF260A5C),
    Color(0xFFB7D4FF) to Color(0xFF1C3566),
)

/**
 * Живая ёлочка: иголки в инее, огоньки, звезда. [ornaments] — сколько игрушек уже висит:
 * каждый день, когда вы писали друг другу или отмечали привычку, добавляется новая.
 */
@Composable
private fun HolidayTree(
    ornaments: Int,
    modifier: Modifier = Modifier,
    running: Boolean,
    onLongPress: (() -> Unit)?,
) {
    val animate = running && EffectsSettings.animate(LocalContext.current)
    val time = rememberEffectTime(fps = 20, running = animate)
    val longPress by rememberUpdatedState(onLongPress)
    val previewTap = if (onLongPress == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { longPress?.invoke() })
    }
    Box(
        modifier
            .then(previewTap)
            .drawWithCache {
                val rnd = java.util.Random(12)
                val d = 1.dp.toPx()
                val w = size.width
                val h = size.height
                val cx = w / 2
                val top = 14 * d
                val base = h - 14 * d
                val greens = listOf(0xFF123825, 0xFF17462E, 0xFF1D5438, 0xFF256443, 0xFF2E744E).map { Color(it) }
                val paths = greens.map { Path() }
                val frost = Path()
                for (tier in 0 until 5) {
                    val ty = top + (base - top) * (tier / 5f) * 0.92f
                    val tb = top + (base - top) * ((tier + 1.35f) / 5f) * 0.92f
                    val half = w * 0.46f * ((tier + 1) / 5f)
                    repeat(190) {
                        val u = rnd.nextFloat()
                        val v = rnd.nextFloat()
                        val y = ty + (tb - ty) * sqrt(v)
                        val spread = half * ((y - ty) / (tb - ty)) * 0.95f + 3 * d
                        val x = cx + (u * 2 - 1) * spread
                        val len = (4f + rnd.nextFloat() * 6f) * d * (w / (92 * d))
                        val dir = if (x < cx) -1f else 1f
                        val p = paths[rnd.nextInt(paths.size)]
                        p.moveTo(x, y)
                        p.lineTo(x + dir * len * 0.8f, y + len * 0.55f)
                        if (rnd.nextFloat() < 0.04f) frost.addOval(Rect(x, y, x + 1.4f * d, y + 1.4f * d))
                    }
                }
                onDrawBehind {
                    glow(Color(0xFFFFB45A), Offset(cx, h * 0.62f), w * 0.85f, 0.18f)
                    drawRect(Color(0xFF3B2A20), topLeft = Offset(cx - 4 * d, base - 2 * d), size = Size(8 * d, 14 * d))
                    paths.forEachIndexed { i, p -> drawPath(p, greens[i], style = Stroke(1.2f * d, cap = StrokeCap.Round)) }
                    drawPath(frost, Color(0xD9F0F6FF))
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val t = time()
            val d = 1.dp.toPx()
            val w = size.width
            val h = size.height
            val cx = w / 2
            val top = 14 * d
            val base = h - 14 * d
            val slots = ArrayList<Offset>()
            for (tier in 0 until 5) {
                val tb = top + (base - top) * ((tier + 1.35f) / 5f) * 0.92f
                val half = w * 0.46f * ((tier + 1) / 5f)
                val count = 4 + tier
                for (k in 0 until count) {
                    val f = (k + 0.5f) / count
                    val p = Offset(cx + (f * 2 - 1) * half * 0.85f, tb - 6 * d - sin(f * PI.toFloat()) * 6 * d)
                    val wave = 0.5f + 0.5f * sin(t * 2f - tier * 1.1f - k * 0.7f)
                    val c = Bulbs[(tier + k) % Bulbs.size]
                    glow(c, p, 10 * d, 0.35f + 0.4f * wave)
                    glow(Color.White, p, 2.6f * d, 0.7f + 0.3f * wave)
                    if (k % 2 == 1) slots += Offset(p.x, p.y - 9 * d)
                }
            }
            slots.take(ornaments.coerceAtLeast(0)).forEachIndexed { i, p ->
                val (c1, c2) = OrnamentColors[i % OrnamentColors.size]
                val r = 3.6f * d
                drawCircle(Brush.radialGradient(listOf(c1, c2, Color(0xFF050208)), Offset(p.x - r * 0.35f, p.y - r * 0.4f), r * 1.4f), r, p)
                drawCircle(Color.White.copy(alpha = 0.8f), r * 0.28f, Offset(p.x - r * 0.38f, p.y - r * 0.42f))
            }
            val star = Offset(cx, top)
            val pulse = 0.75f + 0.25f * sin(t * 1.6f)
            glow(Color(0xFFFFDC8C), star, 26 * d * pulse, 0.6f)
            val shape = Path()
            for (i in 0 until 10) {
                val rr = if (i % 2 == 1) 3.2f * d else 7f * d
                val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
                val x = star.x + cos(a) * rr
                val y = star.y + sin(a) * rr
                if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
            }
            shape.close()
            drawPath(shape, Brush.verticalGradient(listOf(Color(0xFFFFF4D0), Color(0xFFE0A94A)), star.y - 7 * d, star.y + 7 * d))
        }
    }
}

private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val dec: Float, val color: Color, val glitter: Boolean)

private class Rocket(var x: Float, var y: Float, var vy: Float, val at: Float, val color: Color)

/**
 * Салют: в полночь с 31 декабря на 1 января на обоих телефонах в одну секунду, а если приложение
 * в полночь было закрыто — один раз при первом открытии 1 января. [manual] больше нуля — показать сразу.
 */
@Composable
fun HolidaySaluteHost(active: Holidays.Active?, manual: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var play by remember { mutableIntStateOf(0) }
    LaunchedEffect(manual) { if (manual > 0) play++ }
    LaunchedEffect(active?.newYear) {
        val holiday = active ?: return@LaunchedEffect
        val settings = AppSettings(context)
        val key = "salute-${holiday.newYear}"
        val midnight = LocalDate.of(holiday.newYear, 1, 1).atStartOfDay()
        val now = LocalDateTime.now()
        if (now.isBefore(midnight)) {
            val wait = Duration.between(now, midnight).toMillis()
            if (wait > 6 * 60 * 60 * 1000L) return@LaunchedEffect
            delay(wait)
            settings.holidaySalute = key
            play++
        } else if (settings.holidaySalute != key && now.isBefore(midnight.plusDays(1))) {
            delay(900)
            settings.holidaySalute = key
            play++
        }
    }
    if (play > 0) HolidaySalute(play, modifier)
}

@Composable
private fun HolidaySalute(playKey: Int, modifier: Modifier = Modifier) {
    val rockets = remember(playKey) { ArrayList<Rocket>() }
    val sparks = remember(playKey) { ArrayList<Spark>() }
    var frame by remember(playKey) { mutableLongStateOf(0L) }
    var text by remember(playKey) { mutableStateOf(false) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val animate = EffectsSettings.animate(LocalContext.current)
    LaunchedEffect(playKey, area) {
        if (area == IntSize.Zero) return@LaunchedEffect
        val w = area.width.toFloat()
        val h = area.height.toFloat()
        val s = w / 390f
        if (!animate) {
            text = true
            delay(5000)
            text = false
            return@LaunchedEffect
        }
        val rnd = java.util.Random()
        repeat(9) { i ->
            rockets += Rocket(w * (0.13f + rnd.nextFloat() * 0.74f), h, -(11f + rnd.nextFloat() * 3.5f) * s, i * 0.45f + rnd.nextFloat() * 0.2f, Bulbs[i % Bulbs.size])
        }
        val start = withFrameNanos { it }
        var t = 0f
        while (rockets.isNotEmpty() || sparks.isNotEmpty()) {
            withFrameNanos { now -> t = (now - start) / 1_000_000_000f }
            val ri = rockets.iterator()
            while (ri.hasNext()) {
                val k = ri.next()
                if (t < k.at) continue
                k.y += k.vy
                k.vy += 0.12f * s
                if (k.vy >= -0.5f * s) {
                    repeat(110) {
                        val a = rnd.nextFloat() * 6.283f
                        val sp = (1.2f + rnd.nextFloat() * 3.6f) * s
                        sparks += Spark(k.x, k.y, cos(a) * sp, sin(a) * sp, 1f, 0.008f + rnd.nextFloat() * 0.01f, k.color, rnd.nextFloat() < 0.3f)
                    }
                    ri.remove()
                }
            }
            val si = sparks.iterator()
            while (si.hasNext()) {
                val p = si.next()
                p.x += p.vx
                p.y += p.vy
                p.vx *= 0.985f
                p.vy = p.vy * 0.985f + 0.035f * s
                p.life -= p.dec
                if (p.life <= 0f) si.remove()
            }
            if (t > 1.6f) text = true
            frame++
        }
        delay(2500)
        text = false
    }
    Box(modifier.onSizeChanged { area = it }) {
        Canvas(Modifier.matchParentSize()) {
            if (frame < 0) return@Canvas
            val d = 1.dp.toPx()
            val t = frame
            for (k in rockets) {
                if (k.y >= size.height) continue
                drawCircle(Color(0xE6FFE6B4), 1.6f * d, Offset(k.x, k.y))
            }
            for (p in sparks) {
                val flicker = if (p.glitter && (t + p.x.toLong()) % 3L == 0L) 0.2f else 1f
                val a = (p.life * flicker).coerceIn(0f, 1f)
                drawLine(p.color.copy(alpha = a * 0.6f), Offset(p.x - p.vx * 3f, p.y - p.vy * 3f), Offset(p.x, p.y), strokeWidth = 1.4f * d, cap = StrokeCap.Round)
                glow(p.color, Offset(p.x, p.y), 6 * d, a * 0.6f)
            }
        }
        AnimatedVisibility(visible = text, enter = fadeIn(tween(900)), exit = fadeOut(tween(900)), modifier = Modifier.align(Alignment.Center)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x990A0610))
                    .padding(horizontal = 26.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "С Новым годом",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Color(0xFFC99A4A), Color(0xFFFFF0C8), Color(0xFFD9AE5E))),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(text = "вам обоим, в одну секунду", style = TextStyle(color = Color(0xFFE2D6EA), fontSize = 14.sp))
            }
        }
    }
}
