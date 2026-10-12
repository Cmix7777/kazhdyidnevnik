package io.github.cmix7777.kazhdyidnevnik.ui.fx

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import kotlin.math.floor

/**
 * Перелив при свайпе (AGSL, Android 13+): страницы не уезжают вбок, а одна проступает на месте другой,
 * как чернила, растекающиеся от пальца. Край пятна волнистый, по нему бежит свечение двух цветов.
 * Шейдер стоит на обеих страницах с одинаковым пятном: [uInside] = 1 — страница видна внутри пятна,
 * 0 — снаружи. Так содержимое двух страниц никогда не просвечивает друг через друга.
 * Свечение рисует только верхняя страница ([uGlow] = 1).
 */
internal const val INK_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uDp;
uniform float uS;
uniform float uInside;
uniform float uGlow;
uniform float2 uO;
uniform float3 uCA;
uniform float3 uCB;

half4 main(float2 p) {
    float2 q = p / uDp;
    float2 o = uO / uDp;
    float2 wh = uSize / uDp;
    float maxD = length(max(o, wh - o)) + 70.0;
    float s = clamp(uS, 0.0, 1.0);
    float R = s * maxD;
    float2 d = q - o;
    float dist = length(d);
    float raw = dist - R;
    // Далеко от края пятна ничего считать не нужно.
    if (raw > 110.0) {
        return uInside > 0.5 ? half4(0.0) : content.eval(p);
    }
    if (raw < -110.0) {
        return uInside > 0.5 ? content.eval(p) : half4(0.0);
    }
    float bump = sin(3.14159 * s);
    float t = s * 2.5;
    // Мягкий волнистый край: несколько крупных волн по кругу вместо дорогого шума.
    // Пока пятно маленькое, волны слабее, чтобы край не мельчил.
    float ang = atan(d.y, d.x + 0.0001);
    float warp = (sin(ang * 4.0 + t * 1.3) * 0.55 + sin(ang * 7.0 - t * 1.7 + 1.3) * 0.3 + sin(ang * 11.0 + t * 2.3 + 0.7) * 0.15) * 44.0;
    float e = raw + warp * bump * min(1.0, R / 220.0);
    float m = smoothstep(5.0, -5.0, e);
    float mask = mix(1.0 - m, m, uInside);
    float2 dir = d / max(dist, 0.001);
    float near = exp(-abs(e) / 22.0) * bump;
    float push = uInside > 0.5 ? 1.0 : -1.0;
    float4 col = float4(content.eval(p + dir * near * 14.0 * uDp * push)) * mask;
    float3 ink = mix(uCA, uCB, 0.5 + 0.5 * sin(ang * 2.0 + t));
    float g = clamp(exp(-abs(e) / 9.0) * bump * 0.6 + exp(-abs(e) / 24.0) * bump * 0.14, 0.0, 1.0) * uGlow;
    col = float4(ink * g, g) + col * (1.0 - g);
    return half4(col);
}
"""

/** Откуда растекаются чернила: место, где палец коснулся экрана, или имя в переключателе сверху. */
class InkOrigin {
    internal var finger: Offset? = null

    /** Доля ширины, если переключили нажатием на имя: чернила текут сверху от этого места. */
    internal var fromTop: Float? = null

    fun touched(position: Offset) {
        finger = position
        fromTop = null
    }

    fun tappedName(fraction: Float) {
        fromTop = fraction
    }

    internal fun at(width: Float, height: Float): Offset =
        fromTop?.let { Offset(width * it, 0f) } ?: finger ?: Offset(width * 0.5f, height * 0.5f)
}

/** Запоминает, где палец коснулся страниц (касания при этом работают как обычно). */
fun Modifier.inkTouchOrigin(origin: InkOrigin): Modifier = pointerInput(origin) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        origin.touched(down.position)
    }
}

/**
 * Страница [page] пейджера из двух страниц с переливом. Пока свайп не начат, всё как обычно.
 * Во время свайпа обе страницы стоят на месте, а вторая (верхняя) проступает или тает пятном.
 * На старых телефонах и при «меньше анимаций» страницы просто едут вбок.
 */
@Composable
fun Modifier.inkPage(
    pager: PagerState,
    page: Int,
    origin: InkOrigin,
    first: Color,
    second: Color,
): Modifier {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !EffectsSettings.animate(context)) return this
    return this.then(inkLayer(pager, page, origin, first, second))
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun inkLayer(pager: PagerState, page: Int, origin: InkOrigin, first: Color, second: Color): Modifier {
    val shader = remember { runCatching { RuntimeShader(INK_AGSL) }.getOrNull() }
    var failed by remember { mutableStateOf(false) }
    if (shader == null || failed) return Modifier
    return Modifier
        .zIndex(page.toFloat())
        .graphicsLayer {
            val position = pager.currentPage + pager.currentPageOffsetFraction
            val moving = position - floor(position) > 0.0005f && position - floor(position) < 0.9995f
            if (!moving) {
                translationX = 0f
                renderEffect = null
                return@graphicsLayer
            }
            // Страницы не едут: возвращаем каждую на место.
            translationX = (position - page) * size.width
            // Вперёд (с первой на вторую) пятно открывает вторую страницу, назад — первую.
            val forward = pager.settledPage == 0
            val spread = if (forward) position else 1f - position
            val incoming = if (forward) 1 else 0
            val o = origin.at(size.width, size.height)
            try {
                shader.setFloatUniform("uSize", size.width, size.height)
                shader.setFloatUniform("uDp", density)
                shader.setFloatUniform("uS", spread)
                shader.setFloatUniform("uInside", if (page == incoming) 1f else 0f)
                shader.setFloatUniform("uGlow", if (page == 1) 1f else 0f)
                shader.setFloatUniform("uO", o.x, o.y)
                shader.setFloatUniform("uCA", first.red, first.green, first.blue)
                shader.setFloatUniform("uCB", second.red, second.green, second.blue)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            } catch (e: RuntimeException) {
                renderEffect = null
                failed = true
            }
        }
}
