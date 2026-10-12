package io.github.cmix7777.kazhdyidnevnik.ui.fx

private const val NOISE = """
float h21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + float2(1.0, 0.0)), f.x), mix(h21(i + float2(0.0, 1.0)), h21(i + float2(1.0, 1.0)), f.x), f.y);
}

float fbm(float2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return s;
}
"""

/**
 * Погода на стекле (AGSL, Android 13+) поверх содержимого экрана «Сегодня» как RenderEffect:
 * дождь (капли преломляют то, что под ними, стекло чуть запотевает), снег, мороз по краям с оттаиванием
 * под пальцем. Цвета на входе и выходе с учётом прозрачности: между карточками фон экрана прозрачный.
 */
internal const val WEATHER_GLASS_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uDp;
uniform float uTime;
uniform float uMode;
uniform float uAmt;
uniform float uStrength;
uniform float3 uWipe[12];
""" + NOISE + """
// Лёгкое размытие: центр и четыре точки по диагоналям (5 выборок вместо 9).
float4 blur5(float2 p, float r, float4 center) {
    float4 s = center;
    s += float4(content.eval(p + float2(r, r)));
    s += float4(content.eval(p + float2(-r, r)));
    s += float4(content.eval(p + float2(r, -r)));
    s += float4(content.eval(p + float2(-r, -r)));
    return s * 0.2;
}

float snowLayer(float2 q, float t, float sc, float sp, float sz, float density) {
    float2 g = q / sc;
    g.y -= t * sp;
    g.x += sin(g.y * 0.6 + t * 0.4) * 0.35;
    float2 id = floor(g);
    float2 f = fract(g) - 0.5;
    float n = h21(id);
    float2 c = (float2(h21(id + 1.7), h21(id + 5.3)) - 0.5) * 0.6;
    float r = sz * (0.6 + 0.6 * h21(id + 9.1));
    return smoothstep(r, r * 0.2, length(f - c)) * step(1.0 - density, n);
}

half4 main(float2 p) {
    half4 base = content.eval(p);
    float2 q = p / uDp;
    float t = uTime;
    float4 outc = float4(base);

    if (uMode < 1.5) {
        // Неподвижные капли.
        float cs = 46.0;
        float2 g = q / cs;
        float2 id = floor(g);
        float2 f = fract(g) - 0.5;
        float n = h21(id);
        float2 c = (float2(h21(id + 3.1), h21(id + 7.7)) - 0.5) * 0.6;
        float life = fract(t * 0.07 + n);
        float r = mix(0.09, 0.22, h21(id + 11.3)) * smoothstep(0.0, 0.06, life) * smoothstep(1.0, 0.7, life);
        float2 d = f - c;
        float m1 = smoothstep(r, r * 0.6, length(d)) * step(1.0 - 0.7 * uStrength, h21(id + 2.2));
        // Стекающие капли с чистой дорожкой.
        float cw = 54.0;
        float ch = 200.0;
        float colId = floor(q.x / cw);
        float2 g2 = float2(q.x / cw, q.y / ch + h21(float2(colId, 1.7)) * 5.0);
        float2 id2 = floor(g2);
        float2 f2 = float2(fract(g2.x) - 0.5, fract(g2.y));
        float n2 = h21(id2);
        float tt = fract(t * (0.2 + 0.18 * n2) + n2 * 7.0);
        float y = tt * tt;
        float x0 = (n2 - 0.5) * 0.5 + sin(tt * 14.0 + n2 * 6.0) * 0.05 * (1.0 - tt);
        float2 d2 = float2(f2.x - x0, (f2.y - y) * ch / cw);
        float r2 = 0.12 + 0.07 * n2;
        float2 ds = d2 * float2(1.0, d2.y < 0.0 ? 0.75 : 1.15);
        float alive = smoothstep(0.0, 0.04, tt) * smoothstep(1.0, 0.92, tt) * step(1.0 - 0.65 * uStrength, n2);
        float m2 = smoothstep(r2, r2 * 0.6, length(ds)) * alive;
        float trail = smoothstep(0.09, 0.03, abs(d2.x)) * step(d2.y, -r2 * 0.8) * smoothstep(-y * ch / cw - 0.01, 0.0, d2.y) * alive;
        float clearM = clamp(max(m1, m2) + trail * 0.85, 0.0, 1.0);
        // Лёгкая дымка, чтобы текст оставался читаемым.
        float4 fogged = mix(float4(base), blur5(p, 2.0 * uDp, float4(base)) * 0.92 + float4(0.03, 0.035, 0.05, 0.05), 0.3 * uStrength);
        float4 glass = mix(fogged, float4(base), clearM);
        float dropM = max(m1, m2);
        // Преломление и блики считаются только внутри капель: остальной экран не тратит на это время.
        if (dropM > 0.002) {
            float2 lens = d * cs * m1 * -2.2 + d2 * cw * m2 * -2.0;
            float4 refr = float4(content.eval(p + lens * uDp)) * 1.05;
            glass = mix(glass, refr, dropM);
            float hl = smoothstep(0.55, 0.0, length(d / max(r, 0.001) - float2(-0.35, -0.4))) * m1
                     + smoothstep(0.55, 0.0, length(ds / r2 - float2(-0.35, -0.4))) * m2;
            float rimD = m1 * smoothstep(r * 0.55, r * 0.95, length(d)) + m2 * smoothstep(r2 * 0.55, r2 * 0.95, length(ds));
            glass = float4(glass.rgb * (1.0 - rimD * 0.3), glass.a);
            float hla = hl * 0.3;
            glass = float4(glass.rgb * (1.0 - hla) + float3(hla), glass.a + hla * (1.0 - glass.a));
            // Там, где под каплей пустой фон, у капли всё равно видно тело, а не только блик.
            float body = dropM * 0.1 * (1.0 - glass.a);
            glass += float4(float3(0.75, 0.82, 1.0) * body, body);
        }
        outc = mix(float4(base), glass, uAmt);
    } else if (uMode < 2.5) {
        float4 haze = mix(float4(base), float4(base) * 0.9 + float4(0.03, 0.035, 0.05, 0.05), 0.35 * uAmt);
        float density = 0.35 + 0.4 * uStrength;
        float s = snowLayer(q, t, 34.0, 1.1, 0.10, density) + snowLayer(q + 13.0, t, 22.0, 0.75, 0.08, density) * 0.75
                + snowLayer(q + 31.0, t, 14.0, 0.5, 0.07, density) * 0.5;
        float a = clamp(s, 0.0, 1.0) * 0.9 * uAmt;
        outc = float4(float3(0.93, 0.96, 1.0) * a, a) + haze * (1.0 - a);
    } else {
        float2 wh = uSize / uDp;
        float2 uv = q / wh;
        float corner = smoothstep(0.33, 0.7, length((uv - 0.5) * float2(1.0, 0.75)));
        float edge = smoothstep(0.08, 0.0, min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y)));
        float zone = clamp(corner * 1.1 + edge * 0.6, 0.0, 1.0) * uAmt * uStrength;
        // Середина экрана чистая: узоры и размытие считаются только у краёв.
        if (zone > 0.001) {
            float melt = 0.0;
            for (int i = 0; i < 12; i++) {
                float3 w = uWipe[i];
                melt += w.z * smoothstep(36.0, 14.0, length(q - w.xy));
            }
            melt = clamp(melt, 0.0, 1.0);
            float warp = fbm(q * 0.025);
            float fv = fbm(q * 0.06 + warp * 2.5);
            float veins = smoothstep(0.82, 0.98, 1.0 - abs(fv * 2.0 - 1.0));
            float amount = zone * (1.0 - melt);
            amount *= smoothstep(0.25, 0.6, fbm(q * 0.02 + 3.0) + amount * 0.4);
            if (amount > 0.001) {
                float a = amount * 0.7;
                float4 frosted = blur5(p, 3.0 * uDp, float4(base)) * 0.75 + float4(0.11, 0.14, 0.19, 0.25);
                outc = mix(float4(base), frosted, a);
                float va = clamp(veins * amount * 0.25, 0.0, 1.0);
                outc = float4(float3(0.75, 0.86, 1.0) * va, va) + outc * (1.0 - va);
                float2 cell = floor(q * 0.6);
                float sp = pow(h21(cell), 60.0) * (0.5 + 0.5 * sin(t * 3.0 + h21(cell + 3.0) * 40.0)) * amount * 1.2;
                sp = clamp(sp, 0.0, 1.0);
                outc = float4(float3(sp), sp) + outc * (1.0 - sp);
            }
        }
    }
    return half4(outc);
}
"""

/**
 * Живое небо в фоне (AGSL): градиент по времени суток, круглые мерцающие звёзды, изредка падающая звезда,
 * солнце с ореолом, луна в настоящей фазе, облака по прогнозу, подсвеченные на закате.
 */
internal const val SKY_AGSL = """
uniform float2 uSize;
uniform float uDp;
uniform float uTime;
uniform float uHour;
uniform float uRise;
uniform float uSet;
uniform float uMoon;
uniform float uCloud;
uniform float3 uAccent;
""" + NOISE + """
float starLayer(float2 q, float cellSize, float chance, float seed, float t) {
    float2 g = q / cellSize;
    float2 id = floor(g);
    float2 f = fract(g);
    float n = h21(id + seed);
    if (n > chance) { return 0.0; }
    float2 c = float2(h21(id + seed + 1.3), h21(id + seed + 7.1)) * 0.8 + 0.1;
    float size = mix(0.35, 1.25, pow(h21(id + seed + 3.3), 3.0));
    float dist = length((f - c) * cellSize);
    float twinkle = 0.65 + 0.35 * sin(t * (0.8 + 2.2 * h21(id + seed + 9.9)) + n * 60.0);
    float core = smoothstep(size + 0.9, size - 0.3, dist);
    float halo = exp(-dist / (size * 2.2)) * 0.25 * step(0.85, h21(id + seed + 5.5));
    return (core + halo) * twinkle * mix(0.55, 1.0, h21(id + seed + 4.4));
}

half4 main(float2 p) {
    float2 q = p / uDp;
    float2 wh = uSize / uDp;
    float v = q.y / wh.y;
    float h = uHour;
    float t = uTime;
    float dayF = smoothstep(uRise - 0.5, uRise + 1.0, h) * (1.0 - smoothstep(uSet - 1.0, uSet + 0.5, h));
    float tw = clamp(exp(-pow((h - uRise) / 0.8, 2.0)) + exp(-pow((h - uSet) / 0.8, 2.0)), 0.0, 1.0);
    float3 night = mix(float3(0.016, 0.02, 0.06), float3(0.045, 0.045, 0.12), v);
    float3 day = mix(float3(0.06, 0.12, 0.30), float3(0.17, 0.28, 0.56), v);
    float3 dusk = mix(float3(0.11, 0.05, 0.22), mix(float3(0.58, 0.2, 0.42), float3(0.98, 0.48, 0.24), smoothstep(0.55, 1.0, v)), smoothstep(0.2, 1.0, v));
    float3 col = mix(night, day, dayF);
    col = mix(col, dusk, tw * 0.9);
    // Цвет палитры приложения сверху, чтобы фон оставался «своим».
    col += uAccent * 0.16 * exp(-length(q - float2(wh.x * 0.5, -wh.y * 0.05)) / (wh.y * 0.4));

    float starVis = (1.0 - dayF) * (1.0 - tw) * (1.0 - v * 0.75) * (1.0 - uCloud * 0.85);
    float stars = starLayer(q, 14.0, 0.35, 0.0, t) + starLayer(q, 23.0, 0.25, 17.0, t) * 0.8;
    col += float3(0.92, 0.93, 1.0) * stars * starVis;

    // Падающая звезда: раз в полминуты, на долю секунды.
    float period = 27.0;
    float cycle = floor(t / period);
    float s = fract(t / period) * period;
    if (s < 0.9 && starVis > 0.2) {
        float k = s / 0.9;
        float2 a = float2(h21(float2(cycle, 1.0)) * wh.x, (0.05 + 0.25 * h21(float2(cycle, 2.0))) * wh.y);
        float2 dir = normalize(float2(1.0, 0.35 + 0.3 * h21(float2(cycle, 3.0))));
        float2 head = a + dir * k * 220.0;
        float2 pa = q - head;
        float along = dot(pa, -dir);
        float across = length(pa + dir * along);
        float tail = smoothstep(70.0, 0.0, along) * step(0.0, along) * smoothstep(1.2, 0.0, across);
        col += float3(0.95, 0.95, 1.0) * tail * sin(k * 3.14159) * starVis;
    }

    // Солнце с ореолом.
    float sunPos = (h - uRise) / (uSet - uRise);
    if (sunPos > -0.05 && sunPos < 1.05) {
        float2 sp = float2(mix(-0.05, 1.05, sunPos) * wh.x, wh.y * (1.02 - 0.8 * sin(3.14159 * clamp(sunPos, 0.0, 1.0))));
        float dist = length(q - sp);
        float3 sunC = mix(float3(1.0, 0.55, 0.25), float3(1.0, 0.92, 0.75), sin(3.14159 * clamp(sunPos, 0.0, 1.0)));
        col += sunC * (smoothstep(15.0, 13.0, dist) * 0.9 + exp(-dist / 40.0) * 0.25 + exp(-dist / 140.0) * 0.25) * (1.0 - uCloud * 0.6);
    }

    // Луна в настоящей фазе.
    float nightLen = 24.0 - (uSet - uRise);
    float hn = h - uSet;
    if (hn < 0.0) { hn += 24.0; }
    float mpos = hn / nightLen;
    if (mpos > -0.05 && mpos < 1.05) {
        float2 mp = float2(mix(-0.05, 1.05, mpos) * wh.x, wh.y * (0.9 - 0.7 * sin(3.14159 * clamp(mpos, 0.0, 1.0))));
        float2 d = q - mp;
        float r = 11.0;
        float disc = smoothstep(r + 0.8, r - 0.8, length(d));
        float lit = (1.0 - cos(6.2832 * uMoon)) * 0.5;
        float side = uMoon < 0.5 ? -1.0 : 1.0;
        float shadow = smoothstep(r + 0.8, r - 0.8, length(d - float2(side * 2.0 * r * lit, 0.0)));
        col += float3(0.95, 0.93, 0.85) * disc * (1.0 - shadow) * 0.9 * (1.0 - uCloud * 0.7)
             + float3(0.6, 0.65, 0.9) * exp(-length(d) / 34.0) * 0.18 * lit * (1.0 - uCloud * 0.7);
    }

    // Облака: плотность по прогнозу; на закате подсвечены снизу.
    if (uCloud > 0.03) {
        float cl = fbm(q * float2(0.005, 0.011) + float2(t * 0.004, 0.0));
        float cm = smoothstep(0.62 - uCloud * 0.35, 0.85 - uCloud * 0.3, cl) * clamp(uCloud * 1.4, 0.0, 1.0) * (1.0 - v * 0.4);
        float3 cloudNight = float3(0.07, 0.075, 0.12);
        float3 cloudDay = float3(0.45, 0.5, 0.62);
        float3 cloudDusk = mix(float3(0.42, 0.25, 0.38), float3(1.0, 0.6, 0.42), smoothstep(0.3, 1.0, v));
        float3 cloudC = mix(mix(cloudNight, cloudDay, dayF), cloudDusk, tw * 0.85);
        col = mix(col, cloudC, cm * 0.75);
    }
    // Дымка у горизонта.
    col += mix(float3(0.03, 0.03, 0.08), float3(0.25, 0.12, 0.06), tw) * smoothstep(0.7, 1.0, v) * 0.5;
    return half4(half3(col), 1.0);
}
"""
