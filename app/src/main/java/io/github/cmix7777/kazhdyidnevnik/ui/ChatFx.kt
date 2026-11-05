package io.github.cmix7777.kazhdyidnevnik.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberEffectTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Живые эффекты переписки: дымок на фоне, эффекты на слова, «печатает» каплей,
 * пыль при удалении и масштаб ленты щипком. Всё рисуется только пока нужно и выключается
 * вместе с «Меньше анимаций».
 */

// ---------- дымок на фоне ----------

private const val SMOKE_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float3 uA;
uniform float3 uB;
uniform float3 uBg;

float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}
half4 main(float2 fc) {
    float2 uv = fc / uSize.y;
    float t = uTime * 0.045;
    float2 q = float2(fbm(uv * 1.6 + float2(0.0, t)), fbm(uv * 1.6 + float2(5.2, -t * 0.8)));
    float2 r = float2(fbm(uv * 1.4 + 2.2 * q + float2(1.7, 9.2) + t * 0.6), fbm(uv * 1.4 + 2.2 * q + float2(8.3, 2.8) - t * 0.5));
    float f = fbm(uv * 1.2 + 2.6 * r);
    float side = smoothstep(0.1, 0.9, fc.x / uSize.x + (r.x - 0.5) * 0.6);
    float3 smoke = mix(uB, uA, side);
    float dens = smoothstep(0.35, 0.95, f) * (0.55 + 0.45 * smoothstep(1.0, 0.0, fc.y / uSize.y));
    float3 col = uBg + smoke * dens * 0.5;
    return half4(half3(col), 1.0);
}
"""

/**
 * Дымок за сообщениями: медленно плывёт, слева в цветах второго человека, справа — в своих.
 * Рисуется в четверть размера отдельным слоем и растягивается (дым и так размытый), 15 раз в секунду.
 * На телефонах до Android 13 ничего не рисует: остаётся обычный фон.
 */
@Composable
internal fun ChatSmoke(mine: Color, theirs: Color, background: Color, running: Boolean, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    SmokeLayer(mine, theirs, background, running, modifier)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun SmokeLayer(mine: Color, theirs: Color, background: Color, running: Boolean, modifier: Modifier) {
    val shader = remember { runCatching { RuntimeShader(SMOKE_AGSL) }.getOrNull() } ?: return
    val time = rememberEffectTime(fps = 15, running = running)
    val scale = 4f
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .size(maxWidth / scale, maxHeight / scale)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    // Обязательно отдельным слоем: дым считается в четверть размера и растягивается готовой картинкой.
                    // Без этого шейдер считался бы для каждой точки экрана и переписка вставала колом.
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            try {
                shader.setFloatUniform("uSize", size.width, size.height)
                // Пока время стоит («меньше анимаций»), дым замер на красивом месте, а не в самом начале.
                shader.setFloatUniform("uTime", time() + 40f)
                shader.setFloatUniform("uA", mine.red, mine.green, mine.blue)
                shader.setFloatUniform("uB", theirs.red, theirs.green, theirs.blue)
                shader.setFloatUniform("uBg", background.red, background.green, background.blue)
                drawRect(brush = ShaderBrush(shader))
            } catch (e: RuntimeException) {
                // Шейдер не заработал на этом телефоне: остаётся обычный фон.
            }
        }
    }
}

// ---------- эффекты на слова ----------

private val HEART = PathParser().parsePathString(
    "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z",
).toPath()

private fun DrawScope.heart(x: Float, y: Float, s: Float, rotation: Float, color: Color, alpha: Float) {
    withTransform({
        translate(x, y)
        rotate(rotation * 57.2958f, pivot = Offset.Zero)
        scale(s / 24f, s / 24f, pivot = Offset.Zero)
        translate(-12f, -12f)
    }) {
        drawPath(HEART, color, alpha = alpha.coerceIn(0f, 1f))
    }
}

private fun hex(value: Long) = Color(value)

private fun mix(a: Color, b: Color, t: Float, alpha: Float = 1f) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = alpha,
)

/**
 * Один эффект. Время идёт по кадрам ([update]), а рисуется он отдельно ([draw]): так эффект не замедляется,
 * если какой-то кадр не нарисовался, и в тестах кадр в нужную секунду совпадает с телефоном.
 */
internal abstract class WordEffect {
    var done = false

    /** Продвинуть эффект на [dt] секунд. false — он закончился. */
    abstract fun update(dt: Float): Boolean

    abstract fun draw(scope: DrawScope)
}

private class NightFx(private val w: Float, private val h: Float, private val dp: Float) : WordEffect() {
    private class Star(val x: Float, val y: Float, val r: Float, val p: Float, val s: Float)
    private val stars = List(95) { Star(Random.nextFloat() * w, Random.nextFloat() * h * 0.85f, (0.5f + Random.nextFloat() * 1.4f) * dp, Random.nextFloat() * 6.28f, 1.5f + Random.nextFloat() * 3f) }
    private var age = 0f
    private val duration = 3.9f

    override fun update(dt: Float): Boolean {
        age += dt
        return age <= duration
    }

    override fun draw(scope: DrawScope): Unit = with(scope) {
        if (age > duration) return
        val env = min(1f, age / 0.5f) * min(1f, (duration - age) / 0.9f)
        drawRect(Color(0xFF040310), alpha = 0.66f * env)
        for (s in stars) {
            drawCircle(Color.White, s.r, Offset(s.x, s.y), alpha = (env * (0.45f + 0.55f * sin(s.p + age * s.s))).coerceIn(0f, 1f))
        }
        val mx = w * 0.76f
        val my = h * 0.2f
        val r = min(w, h) * 0.075f
        drawCircle(
            Brush.radialGradient(listOf(Color(0x59DCD2FF), Color(0x00DCD2FF)), center = Offset(mx, my), radius = r * 3.4f),
            radius = r * 3.4f,
            center = Offset(mx, my),
            alpha = env,
        )
        // Месяц: круг без сдвинутого круга.
        val moon = Path().apply { addOval(Rect(Offset(mx, my), r)) }
        val cut = Path().apply { addOval(Rect(Offset(mx + r * 0.55f, my - r * 0.3f), r * 0.92f)) }
        val crescent = Path().apply { op(moon, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
        drawPath(crescent, Color(0xFFF3EEFF), alpha = env)
        val sp = (age - 1f) / 0.7f
        if (sp in 0f..1f) {
            val x0 = w * 0.1f
            val y0 = h * 0.1f
            val x1 = w * 0.62f
            val y1 = h * 0.36f
            val hx = x0 + (x1 - x0) * sp
            val hy = y0 + (y1 - y0) * sp
            val tx = hx - (x1 - x0) * 0.2f
            val ty = hy - (y1 - y0) * 0.2f
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.95f * (1f - sp))), start = Offset(tx, ty), end = Offset(hx, hy)),
                Offset(tx, ty), Offset(hx, hy), strokeWidth = 2f * dp, cap = StrokeCap.Round,
            )
        }
    }
}

/** Рассвет маленькой сценой: небо светлеет, из-за холмов встаёт солнце с лучами, облака, птицы. */
private class SunFx(private val w: Float, private val h: Float, private val dp: Float) : WordEffect() {
    private val duration = 5f
    private var age = 0f
    private val hz = h * 0.68f
    private val r = min(w, h) * 0.13f
    private class Star(val x: Float, val y: Float, val r: Float, val p: Float)
    private class Cloud(val x: Float, val y: Float, val s: Float, val v: Float)
    private class Bird(val x0: Float, val y: Float, val s: Float, val sp: Float, val ph: Float, val d: Float)
    private val stars = List(46) { Star(Random.nextFloat() * w, Random.nextFloat() * hz * 0.6f, (0.5f + Random.nextFloat() * 1.1f) * dp, Random.nextFloat() * 6.28f) }
    private val clouds = listOf(Cloud(w * 0.16f, hz * 0.44f, w * 0.36f, 7f * dp), Cloud(w * 0.78f, hz * 0.3f, w * 0.3f, -5f * dp), Cloud(w * 0.58f, hz * 0.62f, w * 0.42f, 4f * dp))
    private val birds = listOf(
        Bird(-30f * dp, hz * 0.38f, 8f * dp, 78f * dp, 0f, 0.7f),
        Bird(-70f * dp, hz * 0.45f, 6f * dp, 72f * dp, 1.4f, 0.9f),
        Bird(-50f * dp, hz * 0.33f, 5.5f * dp, 82f * dp, 2.6f, 1.1f),
    )
    private fun ridge(base: Float, amp: Float, k: Float, ph: Float) = List(33) { i ->
        val t = i / 32f
        Offset(t * w, base - amp * (0.5f + 0.5f * sin(t * PI.toFloat() * k + ph)))
    }
    private val far = ridge(hz + h * 0.03f, h * 0.06f, 2.4f, 0.7f)
    private val near = ridge(hz + h * 0.12f, h * 0.08f, 1.5f, 2.2f)
    private val paint = Paint()

    private fun DrawScope.hills(points: List<Offset>, top: Color, bottom: Color) {
        val path = Path().apply {
            moveTo(0f, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(w, h)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = points.minOf { it.y }, endY = h))
    }

    private fun DrawScope.cloud(x: Float, y: Float, s: Float, rise: Float) {
        val brush = Brush.verticalGradient(
            listOf(mix(hex(0xFF2E2458), hex(0xFFF6C3D2), rise, 0.9f), mix(hex(0xFF5A3270), hex(0xFFFFC98E), rise, 0.9f)),
            startY = y - s * 0.14f,
            endY = y + s * 0.1f,
        )
        val path = Path().apply {
            addOval(Rect(x - s * 0.5f, y - s * 0.085f, x + s * 0.5f, y + s * 0.085f))
            addOval(Rect(x - s * 0.36f, y - s * 0.14f, x + s * 0.04f, y + s * 0.04f))
            addOval(Rect(x - s * 0.14f, y - s * 0.17f, x + s * 0.34f, y + s * 0.03f))
        }
        drawPath(path, brush)
    }

    override fun update(dt: Float): Boolean {
        age += dt
        return age <= duration
    }

    override fun draw(scope: DrawScope): Unit = with(scope) {
        if (age > duration) return
        val env = min(1f, age / 0.6f) * min(1f, (duration - age) / 1.1f)
        val k = ((age - 0.35f) / 3.1f).coerceIn(0f, 1f)
        val rise = if (k < 0.5f) 2f * k * k else 1f - (-2f * k + 2f).pow(2) / 2f
        // Сцена целиком в отдельном слое, потом одной прозрачностью: слои не просвечивают друг через друга.
        paint.alpha = 0.98f * env
        drawIntoCanvas { it.saveLayer(Rect(0f, 0f, w, h), paint) }
        drawRect(
            Brush.verticalGradient(
                0f to mix(hex(0xFF120E30), hex(0xFF6E86C8), rise),
                0.55f to mix(hex(0xFF3E2466), hex(0xFFE897AE), rise),
                1f to mix(hex(0xFF8A3E66), hex(0xFFFFC27E), rise),
                startY = 0f,
                endY = hz + h * 0.05f,
            ),
        )
        for (s in stars) {
            val a = max(0f, 1f - rise * 1.4f) * (0.55f + 0.45f * sin(s.p + age * 3f))
            if (a > 0.01f) drawCircle(Color.White, s.r, Offset(s.x, s.y), alpha = a)
        }
        val sx = w * 0.5f
        val sy = hz + r * 1.2f - rise * r * 2.7f
        drawRect(
            Brush.radialGradient(
                0f to Color(0xCCFFD68C),
                0.35f to Color(0x4DFFAA6E),
                1f to Color(0x00FF9678),
                center = Offset(sx, sy),
                radius = r * 5.5f,
            ),
        )
        rotate(age * 0.07f * 57.2958f, pivot = Offset(sx, sy)) {
            val rays = Brush.radialGradient(listOf(Color(0x52FFE8AF), Color(0x00FFE8AF)), center = Offset(sx, sy), radius = r * 7.5f)
            val len = r * 7.5f
            for (i in 0 until 16) {
                val a = i * (2f * PI.toFloat() / 16f)
                val ray = Path().apply {
                    moveTo(sx, sy)
                    lineTo(sx + cos(a - 0.04f) * len, sy + sin(a - 0.04f) * len)
                    lineTo(sx + cos(a + 0.04f) * len, sy + sin(a + 0.04f) * len)
                    close()
                }
                drawPath(ray, rays, alpha = 0.3f + 0.7f * rise)
            }
        }
        for (c in clouds) cloud(c.x + c.v * age, c.y, c.s, rise)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFF8DE),
                0.7f to Color(0xFFFFD47E),
                1f to Color(0xFFFFB45E),
                center = Offset(sx, sy - r * 0.25f),
                radius = r,
            ),
            radius = r,
            center = Offset(sx, sy),
        )
        hills(far, mix(hex(0xFF2A1B4C), hex(0xFF7A5296), rise), mix(hex(0xFF1E1438), hex(0xFF4A3270), rise))
        val edge = Path().apply { far.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(edge, Color(0xFFFFCD96), alpha = 0.6f * rise, style = Stroke(width = 1.5f * dp))
        hills(near, mix(hex(0xFF150D2A), hex(0xFF3A2660), rise), mix(hex(0xFF0C0818), hex(0xFF22183E), rise))
        for (b in birds) {
            val t = age - b.d
            if (t < 0f) continue
            val x = b.x0 + b.sp * t
            val y = b.y + sin(t * 1.3f + b.ph) * 4f * dp
            val f = sin(t * 9f + b.ph) * b.s * 0.55f
            val bird = Path().apply {
                moveTo(x - b.s, y - f)
                quadraticTo(x - b.s * 0.4f, y - b.s * 0.25f, x, y)
                quadraticTo(x + b.s * 0.4f, y - b.s * 0.25f, x + b.s, y - f)
            }
            drawPath(bird, Color(0xE61C1030), style = Stroke(width = 1.7f * dp, cap = StrokeCap.Round))
        }
        drawIntoCanvas { it.restore() }
    }
}

private class HeartsFx(private val w: Float, private val h: Float, private val dp: Float) : WordEffect() {
    private val colors = listOf(Color(0xFFC084FC), Color(0xFFF0ABFC), Color(0xFF93C5FD), Color(0xFFF472B6), Color(0xFFA78BFA))
    private class H(val x: Float, val y0: Float, val d: Float, val life: Float, val vy: Float, val s: Float, val ph: Float, val sw: Float, val c: Color, val rot: Float)
    private val hearts = List(32) { i ->
        val life = 2.6f + Random.nextFloat() * 1.3f
        H(
            x = Random.nextFloat() * w,
            y0 = h + (20f + Random.nextFloat() * 40f) * dp,
            d = Random.nextFloat() * 1.1f,
            life = life,
            vy = h * (0.8f + Random.nextFloat() * 0.55f) / life,
            s = (10f + Random.nextFloat() * 20f) * dp,
            ph = Random.nextFloat() * 6.28f,
            sw = (12f + Random.nextFloat() * 24f) * dp,
            c = colors[i % colors.size],
            rot = (Random.nextFloat() - 0.5f) * 0.6f,
        )
    }
    private var age = 0f

    private val end = hearts.maxOf { it.d + it.life }

    override fun update(dt: Float): Boolean {
        age += dt
        return age < end
    }

    override fun draw(scope: DrawScope) {
        for (k in hearts) {
            val t = age - k.d
            if (t < 0f || t > k.life) continue
            val y = k.y0 - k.vy * t
            if (y < -40f * dp) continue
            val x = k.x + sin(k.ph + t * 2.2f) * k.sw
            val a = min(1f, t / 0.3f) * min(1f, (k.life - t) / 1f)
            scope.heart(x, y, k.s, k.rot + sin(t * 2f + k.ph) * 0.15f, k.c, a)
        }
    }
}

private class FireFx(private val w: Float, private val h: Float, private val dp: Float) : WordEffect() {
    private val colors = listOf(
        Color(0xFFF0ABFC) to Color(0xFFC084FC),
        Color(0xFF93C5FD) to Color(0xFFBFE3FF),
        Color(0xFFFDE68A) to Color(0xFFF5B86B),
        Color(0xFFF472B6) to Color(0xFFFDA4AF),
        Color(0xFFC4B5FD) to Color.White,
    )
    private class Burst(val t: Float, val x: Float, val y: Float, val c: Pair<Color, Color>, var done: Boolean = false)
    private class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, val life: Float, var age: Float, val c: Color)
    private val plan = listOf(0f, 0.45f, 0.9f, 1.35f, 1.8f).mapIndexed { i, t ->
        Burst(t, w * (0.18f + Random.nextFloat() * 0.64f), h * (0.14f + Random.nextFloat() * 0.36f), colors[i % colors.size])
    }
    private val parts = ArrayList<Part>()
    private var age = 0f

    override fun update(dt: Float): Boolean {
        age += dt
        for (b in plan) {
            if (!b.done && age >= b.t) {
                b.done = true
                repeat(80) { i ->
                    val a = Random.nextFloat() * 6.283f
                    val sp = (60f + Random.nextFloat() * 200f) * dp
                    parts += Part(b.x, b.y, cos(a) * sp, sin(a) * sp, 1.1f + Random.nextFloat() * 0.6f, 0f, if (i % 2 == 0) b.c.first else b.c.second)
                }
            }
        }
        for (p in parts) {
            p.age += dt
            p.vx *= 1f - 1.5f * dt
            p.vy = p.vy * (1f - 1.5f * dt) + 130f * dp * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        parts.removeAll { it.age > it.life }
        return age < 2.2f || parts.isNotEmpty()
    }

    override fun draw(scope: DrawScope) {
        for (p in parts) {
            scope.drawLine(
                p.c,
                Offset(p.x - p.vx * 0.045f, p.y - p.vy * 0.045f),
                Offset(p.x, p.y),
                strokeWidth = 2f * dp,
                cap = StrokeCap.Round,
                alpha = max(0f, 1f - p.age / p.life),
                blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
            )
        }
    }
}

/** Маленький взрыв сердечек от двойного касания. */
private class PopFx(x: Float, y: Float, private val dp: Float) : WordEffect() {
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, val s: Float, val c: Color)
    private val parts = List(10) { i ->
        val a = i / 10f * 6.283f + Random.nextFloat() * 0.3f
        val sp = (90f + Random.nextFloat() * 70f) * dp
        P(x, y, cos(a) * sp, sin(a) * sp, (6f + Random.nextFloat() * 6f) * dp, if (i % 2 == 1) Color(0xFFF472B6) else Color(0xFFC084FC))
    }
    private var age = 0f

    override fun update(dt: Float): Boolean {
        age += dt
        if (age > 0.8f) return false
        for (p in parts) {
            p.vx *= 1f - 3f * dt
            p.vy *= 1f - 3f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        return true
    }

    override fun draw(scope: DrawScope) {
        for (p in parts) scope.heart(p.x, p.y, p.s, 0f, p.c, 1f - age / 0.8f)
    }
}

/** Названия эффектов на слова и подписи для выбора. */
internal object WordFxNames {
    const val HEARTS = "hearts"
    const val NIGHT = "night"
    const val SUN = "sun"
    const val FIRE = "fire"
    const val POP = "pop"
}

/** Очередь эффектов поверх переписки. Один и тот же эффект не повторяется чаще раза в 8 секунд. */
@Stable
internal class WordFxState {
    internal val items = ArrayList<WordEffect>()
    private val pending = ArrayList<Pair<String, Offset?>>()
    private val recent = HashMap<String, Long>()
    internal var count by mutableIntStateOf(0)
    internal var tick by mutableLongStateOf(0L)
    internal var width = 0f
    internal var height = 0f

    fun play(name: String, at: Offset? = null) {
        val now = System.currentTimeMillis()
        if (name != WordFxNames.POP) {
            val last = recent[name]
            if (last != null && now - last < 8_000L) return
            recent[name] = now
        }
        pending += name to at
        count = items.size + pending.size
    }

    internal fun spawn(w: Float, h: Float, dp: Float) {
        if (pending.isEmpty()) return
        for ((name, at) in pending) {
            items += when (name) {
                WordFxNames.NIGHT -> NightFx(w, h, dp)
                WordFxNames.SUN -> SunFx(w, h, dp)
                WordFxNames.FIRE -> FireFx(w, h, dp)
                WordFxNames.POP -> PopFx(at?.x ?: (w / 2f), at?.y ?: (h / 2f), dp)
                else -> HeartsFx(w, h, dp)
            }
        }
        pending.clear()
    }

    /** Один кадр: новые эффекты появляются, у остальных идёт время, закончившиеся убираются. */
    internal fun advance(dt: Float, dp: Float) {
        if (width > 0f) spawn(width, height, dp)
        for (effect in items) {
            if (effect.done) continue
            if (!runCatching { effect.update(dt) }.getOrDefault(false)) effect.done = true
        }
        if (items.removeAll { it.done }) count = items.size + pending.size
    }
}

/** Слой эффектов на весь экран переписки. Пока эффектов нет, ничего не рисует и не просыпается. */
@Composable
internal fun WordFxLayer(state: WordFxState, modifier: Modifier = Modifier) {
    val active = state.count > 0
    val dp = LocalDensity.current.density
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (state.count > 0) {
            val now = withFrameNanos { it }
            state.advance(((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f), dp)
            last = now
            state.tick = state.tick + 1
        }
    }
    if (!active) return
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                state.width = it.width.toFloat()
                state.height = it.height.toFloat()
            },
    ) {
        state.tick
        for (effect in state.items) {
            if (effect.done) continue
            try {
                effect.draw(this)
            } catch (e: RuntimeException) {
                effect.done = true
            }
        }
    }
}

// ---------- «печатает» каплей ----------

/**
 * Три капли пульсируют, а четвёртая перетекает между ними: размытие и порог по прозрачности
 * склеивают их, как жидкость (Android 12+). На старых телефонах — просто три точки.
 */
@Composable
internal fun TypingGoo(first: Color, second: Color, modifier: Modifier = Modifier) {
    val time = rememberEffectTime(fps = 40)
    val goo = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) gooEffect() else null
    }
    Canvas(
        modifier = modifier
            .size(width = 64.dp, height = 26.dp)
            .graphicsLayer {
                if (goo != null) renderEffect = goo
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        val t = time()
        val unit = size.height / 26f
        val cy = size.height / 2f
        val xs = floatArrayOf(13f * unit, 32f * unit, 51f * unit)
        val brush = Brush.horizontalGradient(listOf(first, second), startX = xs[0], endX = xs[2])
        for (i in 0..2) {
            val phase = ((t * 1000f / 1600f) - i * 0.16f).let { it - kotlin.math.floor(it) }
            val s = 0.6f + 0.4f * (0.5f - 0.5f * cos(phase * 2f * PI.toFloat()))
            drawCircle(brush, radius = 5.2f * unit * s, center = Offset(xs[i], cy))
        }
        val travel = 0.5f - 0.5f * cos((t / 1.6f) * 2f * PI.toFloat())
        drawCircle(brush, radius = 4.4f * unit, center = Offset(xs[0] + (xs[2] - xs[0]) * travel, cy))
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun gooEffect(): androidx.compose.ui.graphics.RenderEffect {
    val blur = RenderEffect.createBlurEffect(7f, 7f, android.graphics.Shader.TileMode.DECAL)
    val threshold = ColorMatrixColorFilter(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 19f, -8f * 255f,
        ),
    )
    return RenderEffect.createColorFilterEffect(threshold, blur).asComposeRenderEffect()
}

// ---------- пыль при удалении ----------

private class Dust(val bitmap: ImageBitmap, val origin: Offset, dp: Float) {
    val count: Int
    val x: FloatArray
    val y: FloatArray
    val vx: FloatArray
    val vy: FloatArray
    val delay: FloatArray
    val color: IntArray
    val side: Float
    var age = 0f

    init {
        val src = bitmap.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val step = max(3, sqrt(w * h / 1800f).toInt())
        side = step.toFloat()
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val cs = ArrayList<Int>()
        var py = 0
        while (py < h) {
            var px = 0
            while (px < w) {
                val c = pixels[py * w + px]
                if ((c ushr 24) > 40) {
                    xs += px.toFloat()
                    ys += py.toFloat()
                    cs += c
                }
                px += step
            }
            py += step
        }
        count = xs.size
        x = FloatArray(count) { xs[it] }
        y = FloatArray(count) { ys[it] }
        color = IntArray(count) { cs[it] }
        delay = FloatArray(count) { (x[it] / w.coerceAtLeast(1)) * 0.45f + Random.nextFloat() * 0.12f }
        vx = FloatArray(count) { (40f + Random.nextFloat() * 120f) * dp }
        vy = FloatArray(count) { -(20f + Random.nextFloat() * 80f) * dp }
    }
}

/** Сообщения, которые сейчас рассыпаются. */
@Stable
internal class DustState {
    private val items = ArrayList<Dust>()
    internal var count by mutableIntStateOf(0)
    internal var tick by mutableLongStateOf(0L)

    fun add(bitmap: ImageBitmap, origin: Offset, dp: Float) {
        items += Dust(bitmap, origin, dp)
        count = items.size
    }

    internal fun draw(scope: DrawScope) {
        for (d in items) {
            for (i in 0 until d.count) {
                val t = d.age - d.delay[i]
                val c = Color(d.color[i])
                if (t <= 0f) {
                    scope.drawRect(c, Offset(d.origin.x + d.x[i], d.origin.y + d.y[i]), Size(d.side, d.side))
                } else {
                    val life = 0.9f
                    if (t > life) continue
                    val px = d.origin.x + d.x[i] + d.vx[i] * t
                    val py = d.origin.y + d.y[i] + d.vy[i] * t + 30f * t * t
                    scope.drawRect(c, Offset(px, py), Size(d.side, d.side), alpha = (1f - t / life) * c.alpha)
                }
            }
        }
    }

    internal fun advance(dt: Float) {
        for (d in items) d.age += dt
        if (items.removeAll { it.age > 1.5f }) count = items.size
    }
}

@Composable
internal fun DustLayer(state: DustState, modifier: Modifier = Modifier) {
    val active = state.count > 0
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (state.count > 0) {
            val now = withFrameNanos { it }
            state.advance(((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f))
            last = now
            state.tick = state.tick + 1
        }
    }
    if (!active) return
    Canvas(modifier = modifier.fillMaxSize()) {
        state.tick
        state.draw(this)
    }
}

// ---------- масштаб щипком ----------

/**
 * Масштаб ленты двумя пальцами. Срабатывает, только когда на экране два пальца, и тогда не даёт
 * ленте прокручиваться; одним пальцем лента листается как обычно.
 */
internal fun Modifier.pinchZoom(onZoom: (Float) -> Unit, onEnd: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoomed = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    zoomed = true
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (zoomed) onEnd()
    }
}

/** Месяц для ночного эффекта не нужен вне этого файла; круглая маска для перехода «из капли». */
internal fun DrawScope.revealClip(center: Offset, radius: Float, block: DrawScope.() -> Unit) {
    val path = Path().apply { addOval(Rect(center, radius)) }
    clipPath(path) { block() }
}
