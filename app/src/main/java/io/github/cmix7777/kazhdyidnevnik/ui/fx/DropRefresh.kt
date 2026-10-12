package io.github.cmix7777.kazhdyidnevnik.ui.fx

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Капля для обновления (AGSL): из края страницы вытягивается капля на тонкой шейке, отрывается,
 * становится кольцом с бегущим разрывом, а когда всё обновилось, разрыв закрывается и внутри рисуется галочка.
 * Координаты в dp внутри маленького холста индикатора, цвета на выходе с учётом прозрачности.
 */
internal const val DROP_AGSL = """
uniform float2 uSize;
uniform float uDp;
uniform float uTime;
uniform float3 uSrc;
uniform float uSrcH;
uniform float3 uDrop;
uniform float uK;
uniform float uThread;
uniform float uRing;
uniform float uCheck;
uniform float uAlpha;
uniform float3 uA1;
uniform float3 uA2;

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sdSeg(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 p) {
    float2 q = p / uDp;
    float2 wh = uSize / uDp;
    float dS = 1e4;
    if (uSrcH > 0.05) {
        dS = sdRoundBox(q - uSrc.xy, float2(uSrc.z, uSrcH), uSrcH);
    }
    float2 rel = q - uDrop.xy;
    float rlen = length(rel);
    float dD = rlen - uDrop.z;
    float ang = atan(rel.y, rel.x);
    float gf = fract(ang / 6.2832 + uTime * 1.1);
    float gap = smoothstep(0.0, 0.06, gf) * (1.0 - smoothstep(0.16, 0.26, gf)) * (1.0 - uCheck);
    float ringD = abs(rlen - uDrop.z) - 2.2 + gap * 8.0;
    dD = mix(dD, ringD, uRing);
    if (uDrop.z < 0.05) {
        dD = 1e4;
    }
    float dT = 1e4;
    if (uThread > 0.05) {
        float2 pa = q - uSrc.xy;
        float2 ba = uDrop.xy - uSrc.xy;
        float hh = clamp(dot(pa, ba) / max(dot(ba, ba), 1.0), 0.0, 1.0);
        dT = length(pa - ba * hh) - uThread * (0.45 + 0.55 * abs(2.0 * hh - 1.0));
    }
    float d = smin(smin(dS, dT, max(uK * 0.6, 0.01)), dD, max(uK, 0.01));
    float vy = clamp((q.y - uSrc.y) / max(uDrop.y + uDrop.z - uSrc.y, 24.0), 0.0, 1.0);
    float3 edge = mix(uA1, uA2, vy);
    float cov = 1.0 - smoothstep(-0.8, 0.8, d);
    float glow = exp(-max(d, 0.0) / 10.0) * 0.45 * (1.0 - cov);
    float3 fill = edge * 0.85 + float3(0.08);
    float spec = smoothstep(0.5, 0.0, length(rel / max(uDrop.z, 1.0) - float2(-0.35, -0.45))) * (1.0 - uRing);
    fill += float3(spec * 0.45);
    float3 col = fill * cov + edge * glow;
    float a = clamp(cov + glow, 0.0, 1.0);
    // Галочка рисуется слева направо, пока разрыв кольца закрывается.
    if (uCheck > 0.001) {
        float2 c0 = float2(-5.5, 0.5);
        float2 c1 = float2(-1.5, 4.5);
        float2 c2 = float2(6.0, -4.5);
        float l1 = length(c1 - c0);
        float l2 = length(c2 - c1);
        float drawn = uCheck * (l1 + l2);
        float d1 = sdSeg(rel, c0, c0 + (c1 - c0) * clamp(drawn / l1, 0.001, 1.0));
        float d2 = 1e4;
        if (drawn > l1) {
            d2 = sdSeg(rel, c1, c1 + (c2 - c1) * clamp((drawn - l1) / l2, 0.001, 1.0));
        }
        float ck = 1.0 - smoothstep(1.0, 2.2, min(d1, d2));
        col = mix(col, float3(1.0), ck);
        a = max(a, ck);
        float cg = exp(-min(d1, d2) / 5.0) * 0.35 * (1.0 - ck) * (1.0 - a);
        col += edge * cg;
        a = clamp(a + cg, 0.0, 1.0);
    }
    float k = uAlpha * step(0.0, wh.x);
    return half4(half3(col * k), half(a * k));
}
"""

/** Обновление каплей: что сейчас показывать. Сам процесс обновления идёт как раньше. */
@Stable
class DropRefreshState {
    /** Обновление закончилось: в кольце рисуется галочка, страница ещё не вернулась на место. */
    var done by mutableStateOf(false)
        internal set

    /** В шапке на пару секунд видна подпись «обновлено только что». */
    var justUpdated by mutableStateOf(false)
        internal set

    /** Обновление начато жестом (а не само при запуске): только тогда показываем подпись в шапке. */
    internal var byUser = false
    internal var wasRefreshing = false

    /** Индикатор держит страницу сдвинутой: пока идёт обновление и пока видна галочка. */
    fun holding(refreshing: Boolean): Boolean = refreshing || done

    fun pulled() {
        byUser = true
    }
}

/** Состояние капли: после конца обновления показывает галочку, потом подпись в шапке. */
@Composable
fun rememberDropRefresh(refreshing: Boolean, failed: Boolean): DropRefreshState {
    val state = remember { DropRefreshState() }
    val failedNow by rememberUpdatedState(failed)
    LaunchedEffect(refreshing) {
        if (refreshing) {
            state.wasRefreshing = true
            state.justUpdated = false
            return@LaunchedEffect
        }
        if (!state.wasRefreshing) return@LaunchedEffect
        state.wasRefreshing = false
        if (failedNow) {
            state.byUser = false
            return@LaunchedEffect
        }
        try {
            state.done = true
            delay(DONE_HOLD_MS)
        } finally {
            state.done = false
        }
        if (state.byUser) {
            state.byUser = false
            try {
                state.justUpdated = true
                delay(LABEL_MS)
            } finally {
                state.justUpdated = false
            }
        }
    }
    return state
}

private const val DONE_HOLD_MS = 950L
private const val LABEL_MS = 3_000L

/** Насколько сдвинута страница (в долях порога) — столько же места получает капля. */
@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.dropShift(state: PullToRefreshState): Modifier = graphicsLayer {
    translationY = state.distanceFraction.coerceAtLeast(0f) * PullToRefreshDefaults.PositionalThreshold.toPx()
}

/**
 * Индикатор-капля для PullToRefreshBox. Рисуется в промежутке, который освобождает [dropShift].
 * Пока тянешь, телефон даёт короткие тики, чем дальше, тем плотнее (натяжение капли).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropRefreshIndicator(
    state: PullToRefreshState,
    drop: DropRefreshState,
    refreshing: Boolean,
    colorTop: Color,
    colorBottom: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val holding = drop.holding(refreshing)
    val animate = EffectsSettings.animate(context)

    val detach = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    LaunchedEffect(holding) {
        if (holding) {
            detach.animateTo(1f, tween(350))
        } else {
            detach.snapTo(0f)
        }
    }
    LaunchedEffect(refreshing, holding) {
        if (refreshing) {
            ring.animateTo(1f, tween(250, delayMillis = 250))
        } else if (!holding) {
            ring.snapTo(0f)
        }
    }
    LaunchedEffect(drop.done, holding) {
        if (drop.done) {
            ring.snapTo(1f)
            check.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        } else if (!holding) {
            check.snapTo(0f)
        }
    }
    // Натяжение: тик на каждые 9 dp, сильнее по мере вытягивания; на пороге — тик посильнее.
    val holdingNow by rememberUpdatedState(holding)
    LaunchedEffect(state) {
        var lastStep = 0
        var crossed = false
        snapshotFlow { state.distanceFraction }.collect { fraction ->
            val pulled = fraction * PullToRefreshDefaults.PositionalThreshold.value
            if (holdingNow) {
                lastStep = 0
                crossed = false
                return@collect
            }
            if (pulled < 2f) {
                lastStep = 0
                crossed = false
                return@collect
            }
            val step = floor(pulled / 9f).toInt()
            if (fraction >= 1f && !crossed) {
                crossed = true
                Haptics.tension(context, 1f)
            } else if (step > lastStep) {
                Haptics.tension(context, min(0.25f + pulled / 110f, 0.8f))
            }
            if (fraction < 0.9f) crossed = false
            lastStep = step
        }
    }

    val spin = rememberEffectTime(fps = 60, running = animate && refreshing)
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) createDropShader() else null
    }
    var broken by remember { mutableStateOf(false) }
    Canvas(modifier = modifier.size(120.dp, 130.dp)) {
        val gap = state.distanceFraction.coerceAtLeast(0f) * PullToRefreshDefaults.PositionalThreshold.toPx() / density
        if (gap < 0.5f && !holding) return@Canvas
        val geometry = dropGeometry(gap, detach.value, ring.value, holding)
        val time = spin()
        if (shader != null && !broken && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            broken = !drawDropShader(shader, geometry, time, check.value, colorTop, colorBottom)
        } else {
            drawDropSimple(geometry, time, check.value, colorTop, colorBottom)
        }
    }
}

/** Где капля и какой она формы при данном промежутке (в dp). */
internal class DropGeometry(
    val centerX: Float,
    val sourceY: Float,
    val sourceHalfWidth: Float,
    val sourceThickness: Float,
    val dropY: Float,
    val radius: Float,
    val smooth: Float,
    val thread: Float,
    val ring: Float,
    val alpha: Float,
)

internal fun dropGeometry(gap: Float, detach: Float, ring: Float, holding: Boolean): DropGeometry {
    val centerX = 60f
    val sourceY = 2f
    return if (!holding) {
        // Тянем: капля висит на шейке, растёт и опускается вслед за пальцем.
        DropGeometry(
            centerX = centerX,
            sourceY = sourceY,
            sourceHalfWidth = 30f,
            sourceThickness = min(2.5f + gap * 0.03f, 5f),
            dropY = sourceY + 4f + gap * 0.6f,
            radius = min(15f, 4f + gap * 0.14f),
            smooth = 18f + gap * 0.2f,
            thread = max(1.2f, 5f - gap * 0.03f),
            ring = 0f,
            alpha = (gap / 12f).coerceIn(0f, 1f),
        )
    } else {
        // Оторвалась: шейка тает, капля встаёт в середину промежутка и становится кольцом.
        val r = 15f - 2f * detach
        DropGeometry(
            centerX = centerX,
            sourceY = sourceY,
            sourceHalfWidth = 30f,
            sourceThickness = 4f * (1f - detach),
            dropY = max(gap * 0.5f, sourceY + r * 0.6f),
            radius = r,
            smooth = 26f * (1f - detach),
            thread = 0f,
            ring = ring,
            alpha = (gap / 30f).coerceIn(0f, 1f),
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createDropShader(): RuntimeShader? = runCatching { RuntimeShader(DROP_AGSL) }.getOrNull()

/** Рисует каплю шейдером. false — шейдер не сработал, дальше рисуем простым способом. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawDropShader(
    shader: RuntimeShader,
    g: DropGeometry,
    time: Float,
    check: Float,
    top: Color,
    bottom: Color,
): Boolean = try {
    shader.setFloatUniform("uSize", size.width, size.height)
    shader.setFloatUniform("uDp", density)
    shader.setFloatUniform("uTime", time)
    shader.setFloatUniform("uSrc", g.centerX, g.sourceY, g.sourceHalfWidth)
    shader.setFloatUniform("uSrcH", g.sourceThickness)
    shader.setFloatUniform("uDrop", g.centerX, g.dropY, g.radius)
    shader.setFloatUniform("uK", g.smooth)
    shader.setFloatUniform("uThread", g.thread)
    shader.setFloatUniform("uRing", g.ring)
    shader.setFloatUniform("uCheck", check)
    shader.setFloatUniform("uAlpha", g.alpha)
    shader.setFloatUniform("uA1", top.red, top.green, top.blue)
    shader.setFloatUniform("uA2", bottom.red, bottom.green, bottom.blue)
    drawRect(brush = ShaderBrush(shader))
    true
} catch (e: RuntimeException) {
    false
}

/** Для Android до 13: капля кружком, кольцо дугой, галочка линиями. */
private fun DrawScope.drawDropSimple(g: DropGeometry, time: Float, check: Float, top: Color, bottom: Color) {
    val px = density
    val center = Offset(g.centerX * px, g.dropY * px)
    val color = lerp(top, bottom, 0.5f).copy(alpha = g.alpha)
    if (g.ring < 0.5f) {
        if (g.thread > 0f) {
            drawLine(color, Offset(g.centerX * px, g.sourceY * px), center, strokeWidth = g.thread * px, cap = StrokeCap.Round)
        }
        drawCircle(color, radius = g.radius * px, center = center)
    } else {
        val sweep = if (check > 0f) 360f else 300f
        drawArc(
            color = color,
            startAngle = if (check > 0f) 0f else (time * 396f) % 360f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - g.radius * px, center.y - g.radius * px),
            size = Size(g.radius * 2 * px, g.radius * 2 * px),
            style = Stroke(width = 3f * px, cap = StrokeCap.Round),
        )
        if (check > 0f) {
            val path = Path().apply {
                moveTo(center.x - 5.5f * px, center.y + 0.5f * px)
                lineTo(center.x - 1.5f * px, center.y + 4.5f * px)
                lineTo(center.x + 6f * px, center.y - 4.5f * px)
            }
            drawPath(path, Color.White.copy(alpha = g.alpha * check), style = Stroke(width = 2.6f * px, cap = StrokeCap.Round))
        }
    }
}
