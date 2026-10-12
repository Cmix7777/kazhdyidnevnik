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
 * Перелив при свайпе (AGSL, Android 13+): страница не уезжает вбок, а вторая проступает поверх,
 * как чернила, растекающиеся от пальца. Край пятна живой, по нему бежит свечение двух цветов.
 * [uInvert] = 1: пятно не открывает верхнюю страницу, а наоборот прожигает её, открывая нижнюю.
 */
internal const val INK_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uDp;
uniform float uS;
uniform float uInvert;
uniform float2 uO;
uniform float3 uCA;
uniform float3 uCB;
""" + NOISE + """
half4 main(float2 p) {
    float2 q = p / uDp;
    float2 o = uO / uDp;
    float2 wh = uSize / uDp;
    float maxD = length(max(o, wh - o)) + 90.0;
    float s = clamp(uS, 0.0, 1.0);
    float R = s * maxD;
    float raw = length(q - o) - R;
    // Далеко от края пятна ничего считать не нужно.
    if (raw > 160.0) {
        return uInvert > 0.5 ? content.eval(p) : half4(0.0);
    }
    if (raw < -160.0) {
        return uInvert > 0.5 ? half4(0.0) : content.eval(p);
    }
    float bump = sin(3.14159 * s);
    float t = s * 2.5;
    float warp = (fbm(q * 0.012 + float2(t * 0.25, 0.0)) - 0.5) * 90.0 * bump + (vnoise(q * 0.05 - t * 0.4) - 0.5) * 14.0 * bump;
    float e = raw + warp;
    float m = smoothstep(5.0, -5.0, e);
    float mask = mix(m, 1.0 - m, uInvert);
    float2 dir = normalize(q - o + 0.0001);
    float near = exp(-abs(e) / 22.0) * bump;
    float push = uInvert > 0.5 ? -1.0 : 1.0;
    float4 col = float4(content.eval(p + dir * near * 14.0 * uDp * push)) * mask;
    float3 ink = mix(uCA, uCB, 0.5 + 0.5 * sin(atan(dir.y, dir.x) * 2.0 + t));
    float g = clamp(exp(-abs(e) / 9.0) * bump * 0.6 + exp(-abs(e) / 40.0) * bump * 0.14, 0.0, 1.0);
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
            if (page != 1) {
                renderEffect = null
                return@graphicsLayer
            }
            // С первой страницы на вторую пятно открывает вторую; обратно — прожигает её.
            val forward = pager.settledPage == 0
            val spread = if (forward) position else 1f - position
            val o = origin.at(size.width, size.height)
            try {
                shader.setFloatUniform("uSize", size.width, size.height)
                shader.setFloatUniform("uDp", density)
                shader.setFloatUniform("uS", spread)
                shader.setFloatUniform("uInvert", if (forward) 0f else 1f)
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
