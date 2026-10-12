package io.github.cmix7777.kazhdyidnevnik.ui.fx

/*
 * Шейдеры особых дней (AGSL, Android 13+). Они рисуют поверх приложения: тёмную вуаль, которая потом
 * открывается волной, и сам эффект. Цвет на выходе с учётом прозрачности (premultiplied), поэтому экран
 * под вуалью виден, когда анимация кончается. Все размеры в dp: p / uDp. После конца анимации ничего не
 * остаётся: частицы исчезают целиком, а свечение гаснет вместе с ними.
 */

private const val COMMON = """
uniform float2 uSize;
uniform float uDp;
uniform float uT;

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

float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float sdSeg(float2 p, float2 a, float2 b, float r) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}

float bitOf(float m, float i) {
    return mod(floor(m / pow(2.0, i) + 0.001), 2.0);
}

float digitMask(float n) {
    if (n < 0.5) { return 63.0; }
    if (n < 1.5) { return 6.0; }
    if (n < 2.5) { return 91.0; }
    if (n < 3.5) { return 79.0; }
    if (n < 4.5) { return 102.0; }
    if (n < 5.5) { return 109.0; }
    if (n < 6.5) { return 125.0; }
    if (n < 7.5) { return 7.0; }
    if (n < 8.5) { return 127.0; }
    return 111.0;
}

// Цифра из семи сегментов со скруглёнными, слипшимися стыками.
float sdDigit(float2 p, float n, float w, float h, float r) {
    if (n < -0.5) { return 10000.0; }
    float m = digitMask(n);
    float d = 10000.0;
    for (int i = 0; i < 7; i++) {
        if (bitOf(m, float(i)) > 0.5) {
            float2 a = float2(-w, 0.0);
            float2 b = float2(w, 0.0);
            if (i == 0) { a = float2(-w, -h); b = float2(w, -h); }
            if (i == 1) { a = float2(w, -h); b = float2(w, 0.0); }
            if (i == 2) { a = float2(w, 0.0); b = float2(w, h); }
            if (i == 3) { a = float2(-w, h); b = float2(w, h); }
            if (i == 4) { a = float2(-w, 0.0); b = float2(-w, h); }
            if (i == 5) { a = float2(-w, -h); b = float2(-w, 0.0); }
            d = smin(d, sdSeg(p, a, b, r), r * 0.8);
        }
    }
    return d;
}

// Слой поверх слоя (оба с учётом прозрачности).
float4 over(float4 top, float4 bottom) {
    return top + bottom * (1.0 - top.a);
}

// Свечение как полупрозрачный цветной слой.
float4 glowLayer(float3 light) {
    float a = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
    return float4(min(light, float3(a)), a);
}
"""

/** Годовщина: два цвета летят навстречу, сливаются и расходятся волной, которая открывает экран. */
internal const val ANNIVERSARY_AGSL = COMMON + """
uniform float3 uCA;
uniform float3 uCB;

half4 main(float2 p) {
    float2 q = p / uDp;
    float2 wh = uSize / uDp;
    float2 c = float2(wh.x * 0.5, wh.y * 0.46);
    float a = clamp(uT / 0.9, 0.0, 1.0);
    float ea = a * a * (3.0 - 2.0 * a);
    float r0 = 30.0;
    float2 pa = float2(mix(-r0, c.x - 6.0, ea), c.y - sin(ea * 3.14159) * 30.0);
    float2 pb = float2(mix(wh.x + r0, c.x + 6.0, ea), c.y + sin(ea * 3.14159) * 30.0);
    float pulse = 1.0 + 0.1 * smoothstep(0.85, 1.2, uT) + 0.06 * sin(max(uT - 1.2, 0.0) * 5.0) * step(1.2, uT);
    float dA = length(q - pa) - r0 * pulse;
    float dB = length(q - pb) - r0 * pulse;
    float d = smin(dA, dB, 40.0);
    float w = clamp((uT - 2.05) / 1.3, 0.0, 1.0);
    float ew = w * w * (3.0 - 2.0 * w);
    float R = ew * length(wh) * 0.85;
    float ang = atan(q.y - c.y, q.x - c.x);
    float rr = length(q - c) + (fbm(float2(ang * 2.0, uT * 0.8)) - 0.5) * 36.0 * (1.0 - w);
    float reveal = smoothstep(R + 4.0, R - 4.0, rr) * step(0.001, w);
    float blobFade = 1.0 - smoothstep(0.0, 0.25, w);
    float endFade = 1.0 - smoothstep(3.2, 3.5, uT);
    float veil = (1.0 - reveal) * 0.97 * endFade * smoothstep(0.0, 0.25, uT);
    float4 outc = float4(float3(0.03, 0.02, 0.05) * veil, veil);

    float side = clamp(0.5 + (dB - dA) / 80.0, 0.0, 1.0);
    float3 cc = mix(uCB, uCA, side);
    float3 rc = mix(uCA, uCB, 0.5 + 0.5 * sin(ang * 2.0 + uT * 2.0));
    float on = step(0.001, w) * (1.0 - w * 0.7);
    float3 light = cc * exp(-max(d, 0.0) / 18.0) * 0.7 * blobFade
        + rc * exp(-abs(rr - R) / 7.0) * on * 1.3
        + rc * exp(-abs(rr - R) / 40.0) * 0.25 * (1.0 - w) * step(0.001, w);
    float2 g = q / 16.0;
    float2 id = floor(g);
    float2 spo = (float2(h21(id + 2.0), h21(id + 5.0)) - 0.5) * 0.8;
    float spark = smoothstep(0.12, 0.0, length(fract(g) - 0.5 - spo)) * step(0.82, h21(id))
        * exp(-abs(rr - R + 20.0) / 30.0) * step(0.001, w) * (1.0 - w) * 1.5;
    light = (light + float3(spark)) * endFade;
    outc = over(glowLayer(light), outc);

    float cov = (1.0 - smoothstep(-0.8, 0.8, d)) * blobFade * endFade;
    outc = over(float4((cc * 0.85 + float3(0.15)) * cov, cov), outc);
    return half4(outc);
}
"""

/** День рождения: пузыри цвета именинника поднимаются, слипаются в цифру возраста (или сердце) и лопаются. */
internal const val BIRTHDAY_AGSL = COMMON + """
uniform float3 uC;
uniform float3 uC2;
uniform float2 uDig;
uniform float uHeart;

float sdHeart(float2 p) {
    p.x = abs(p.x);
    if (p.y + p.x > 1.0) {
        return length(p - float2(0.25, 0.75)) - 0.35355;
    }
    float2 a = p - float2(0.0, 1.0);
    float2 b = p - 0.5 * max(p.x + p.y, 0.0);
    return sqrt(min(dot(a, a), dot(b, b))) * sign(p.x - p.y);
}

half4 main(float2 p) {
    float2 q = p / uDp;
    float2 wh = uSize / uDp;
    float2 c = float2(wh.x * 0.5, wh.y * 0.48);
    float dB = 10000.0;
    for (int i = 0; i < 14; i++) {
        float fi = float(i);
        float h1 = h21(float2(fi, 1.3));
        float h2 = h21(float2(fi, 7.1));
        float h3 = h21(float2(fi, 3.7));
        float delay = h1 * 0.45;
        float e = clamp((uT - delay) / 1.15, 0.0, 1.0);
        e = 1.0 - pow(1.0 - e, 3.0);
        float2 st = float2(wh.x * (0.1 + 0.8 * h2), wh.y + 30.0 + h3 * 140.0);
        float2 tg = c + (float2(h3, h1) - 0.5) * float2(70.0, 90.0);
        float2 pos = mix(st, tg, e) + float2(sin(uT * 3.0 + fi) * 10.0 * (1.0 - e), 0.0);
        float r = (7.0 + 10.0 * h2) * step(delay, uT);
        dB = smin(dB, length(q - pos) - r, 16.0);
    }
    float m = smoothstep(1.05, 1.75, uT);
    float dS = 10000.0;
    if (uHeart > 0.5) {
        float2 hp = (q - c) / 84.0;
        hp.y = -hp.y + 0.5;
        dS = sdHeart(hp) * 84.0;
    } else {
        float gx = 31.0;
        if (uDig.x < -0.5) { gx = 0.0; }
        dS = min(sdDigit(q - c + float2(gx, 0.0), uDig.x, 17.0, 34.0, 9.0), sdDigit(q - c - float2(gx, 0.0), uDig.y, 17.0, 34.0, 9.0));
    }
    float hold = smoothstep(1.75, 1.95, uT) * (1.0 - smoothstep(2.8, 2.9, uT));
    dS += sin(q.y * 0.12 + uT * 4.0) * 1.2 * hold;
    float b = clamp((uT - 2.85) / 0.7, 0.0, 1.0);
    float d = smin(dB + m * 30.0 + b * 80.0, dS + (1.0 - m) * 70.0 + b * 80.0, 20.0);
    // Брызги считаются только после взрыва, до него цикл не нужен.
    if (b > 0.0) {
        for (int i = 0; i < 24; i++) {
            float fi = float(i);
            float ang = fi / 24.0 * 6.2832 + h21(float2(fi, 2.0)) * 0.4;
            float v = 160.0 + 160.0 * h21(float2(fi, 9.0));
            float2 pos = c + float2(cos(ang), sin(ang)) * v * b + float2(0.0, 380.0 * b * b);
            float r = (4.0 + 5.0 * h21(float2(fi, 4.0))) * (1.0 - b) * step(0.001, b);
            if (r > 0.4) {
                d = min(d, length(q - pos) - r);
            }
        }
    }
    float endFade = 1.0 - smoothstep(3.3, 3.6, uT);
    float veil = (1.0 - smoothstep(2.9, 3.5, uT)) * smoothstep(0.0, 0.3, uT) * 0.95;
    float4 outc = float4(float3(0.03, 0.02, 0.05) * veil, veil);
    float vy = clamp((q.y - c.y + 60.0) / 120.0, 0.0, 1.0);
    float3 cc = mix(uC, uC2, vy);
    outc = over(glowLayer(cc * exp(-max(d, 0.0) / 16.0) * 0.7 * endFade), outc);
    float cov = (1.0 - smoothstep(-0.8, 0.8, d)) * endFade;
    float3 fill = cc * 0.9 + float3(0.1) + float3(0.22) * exp(min(d, 0.0) / 5.0);
    outc = over(float4(min(fill, float3(1.0)) * cov, cov), outc);
    return half4(outc);
}
"""

/** Новый год: снежинки закручиваются вихрем, собираются в цифры года, искрятся и осыпаются. */
internal const val NEW_YEAR_AGSL = COMMON + """
uniform float4 uYear;
uniform float3 uCA;
uniform float3 uCB;

float snowL(float2 q, float t, float sc, float sp, float sz) {
    float2 g = q / sc;
    g.y -= t * sp;
    g.x += sin(g.y * 0.6 + t * 0.4) * 0.35;
    float2 id = floor(g);
    float2 f = fract(g) - 0.5;
    float n = h21(id);
    float2 c = (float2(h21(id + 1.7), h21(id + 5.3)) - 0.5) * 0.6;
    float r = sz * (0.6 + 0.6 * h21(id + 9.1));
    return smoothstep(r, r * 0.2, length(f - c)) * step(0.45, n);
}

half4 main(float2 p) {
    float2 q = p / uDp;
    float2 wh = uSize / uDp;
    float2 c = float2(wh.x * 0.5, wh.y * 0.44);
    float endFade = 1.0 - smoothstep(4.0, 4.3, uT);
    float veil = (1.0 - smoothstep(3.7, 4.3, uT)) * smoothstep(0.0, 0.3, uT) * 0.95;
    float4 outc = float4(float3(0.02, 0.03, 0.06) * veil, veil);

    float snow = snowL(q, uT, 30.0, 0.8, 0.08) * 0.6 + snowL(q + 7.0, uT, 18.0, 0.5, 0.07) * 0.4;
    float sw = clamp(uT / 1.5, 0.0, 1.0);
    float esw = sw * sw * (3.0 - 2.0 * sw);
    float flakes = 0.0;
    if (uT < 1.95) {
    for (int i = 0; i < 48; i++) {
        float fi = float(i);
        // Дешёвые «случайные» числа для каждой снежинки: золотое сечение вместо хеша.
        float h1 = fract(fi * 0.618034 + 0.13);
        float h2 = fract(fi * 0.414214 + 0.57);
        float ang = h1 * 6.2832 + uT * (2.0 + 2.0 * h2);
        float R = mix(260.0 + 160.0 * h2, 20.0 + 80.0 * h1, esw);
        float2 pos = c + float2(cos(ang), sin(ang) * 0.55) * R;
        flakes += smoothstep(3.2, 0.8, length(q - pos));
    }
    }
    float white = snow * veil * 0.7 + clamp(flakes, 0.0, 1.0) * (1.0 - smoothstep(1.3, 1.9, uT)) * step(0.001, uT);

    float sp = 50.0;
    float dd0 = min(min(sdDigit(q - c + float2(sp * 1.5, 0.0), uYear.x, 15.0, 28.0, 7.0), sdDigit(q - c + float2(sp * 0.5, 0.0), uYear.y, 15.0, 28.0, 7.0)),
                    min(sdDigit(q - c - float2(sp * 0.5, 0.0), uYear.z, 15.0, 28.0, 7.0), sdDigit(q - c - float2(sp * 1.5, 0.0), uYear.w, 15.0, 28.0, 7.0)));
    float grow = smoothstep(1.2, 2.0, uT);
    float crumble = clamp((uT - 2.9) / 0.9, 0.0, 1.0);
    float3 light = float3(white);
    float cov = 0.0;
    float3 snowC = float3(1.0);
    // Шум для цифр считается только рядом с ними, остальной экран его не трогает.
    if (dd0 < 60.0) {
        float dd = dd0 + (fbm(q * 0.25) - 0.5) * 5.0;
        float3 tint = mix(uCA, uCB, 0.5 + 0.5 * sin(q.x * 0.02 + uT));
        light += tint * exp(-max(dd, 0.0) / 14.0) * 0.5 * grow * (1.0 - crumble);
        if (dd < 1.0) {
            float visible = step(fbm(q * 0.09), grow * 1.2) * step(crumble * 1.15, fbm(q * 0.06 + float2(0.0, -crumble * 1.5)) + 0.1);
            cov = (1.0 - smoothstep(-0.8, 0.8, dd)) * visible;
            float2 cell = floor(q * 0.5);
            float sparkle = pow(h21(cell), 50.0) * (0.5 + 0.5 * sin(uT * 5.0 + h21(cell + 1.0) * 30.0));
            snowC = mix(float3(0.85, 0.92, 1.0), float3(1.0), fbm(q * 0.3)) + float3(sparkle * 1.5);
        }
    }
    outc = over(glowLayer(light * endFade), outc);
    outc = over(float4(min(snowC, float3(1.0)) * cov, cov), outc);

    // Осыпающиеся комья: падают вниз за экран и гаснут, на месте ничего не остаётся.
    float fall = max((uT - 2.9) / 0.9, 0.0);
    float chunks = 0.0;
    if (fall > 0.0) {
    for (int i = 0; i < 30; i++) {
        float fi = float(i);
        float2 st = c + (float2(h21(float2(fi, 3.0)), h21(float2(fi, 8.0))) - 0.5) * float2(180.0, 60.0);
        float ft = max(fall - h21(float2(fi, 6.0)) * 0.5, 0.0);
        float2 pos = st + float2((h21(float2(fi, 2.0)) - 0.5) * 40.0 * ft, 520.0 * ft * ft);
        float r = (2.0 + 3.0 * h21(float2(fi, 4.0))) * (1.0 - min(ft, 1.0) * 0.5);
        if (ft > 0.0) {
            chunks = max(chunks, smoothstep(r, r - 1.2, length(q - pos)));
        }
    }
    }
    chunks *= endFade;
    outc = over(float4(float3(0.95, 0.97, 1.0) * chunks, chunks), outc);
    return half4(outc);
}
"""
