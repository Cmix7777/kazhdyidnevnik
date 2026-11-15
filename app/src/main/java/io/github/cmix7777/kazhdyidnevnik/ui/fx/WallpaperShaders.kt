package io.github.cmix7777.kazhdyidnevnik.ui.fx

/*
 * Живые фоны переписки на AGSL. У каждого фона своя функция scene(uv, p, t): uv — точка экрана от 0 до 1
 * (y вниз), p — та же точка от середины экрана в долях высоты, t — секунды. Она возвращает свет поверх
 * тёмного фона; общая часть ниже добавляет оттенок, яркость, круги от касаний, волну от нового сообщения
 * и переводит картинку в светлую тему (свет становится краской на светлой бумаге).
 *
 * Файл собран скриптом из отдельных сцен: каждая проверена компилятором Skia (это тот же язык, что AGSL)
 * и просмотрена глазами в тёмной и светлой теме. Руками сюда лучше не лезть — править сцены и собирать заново.
 */

internal const val WALL_HEAD_AGSL = SSTEP_AGSL + """
uniform float2 uSize;
uniform float uTime;
uniform float uDark;
uniform float3 uBg;
uniform float3 uInk;
uniform float uHue;
uniform float uPower;
uniform float2 uTilt;
uniform float3 uTouch;
uniform float uPulse;

float h11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}
float h21(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float2 h22(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * float3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}
float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + float2(1.0, 0.0)), u.x), mix(h21(i + float2(0.0, 1.0)), h21(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = float2(p.x * 1.6 - p.y * 1.2, p.x * 1.2 + p.y * 1.6) + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}
float fbm2(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 2; i++) {
        v += a * vnoise(p);
        p = float2(p.x * 1.6 - p.y * 1.2, p.x * 1.2 + p.y * 1.6) + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v / 0.75;
}
float2 rot(float2 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return float2(p.x * c - p.y * s, p.x * s + p.y * c);
}
float3 hueShift(float3 c, float a) {
    float3 k = float3(0.57735, 0.57735, 0.57735);
    float ca = cos(a);
    float sa = sin(a);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}
float3 pal(float t, float3 a, float3 b, float3 c, float3 d) {
    return a + b * cos(6.28318 * (c * t + d));
}
// Звёзды: q — координаты в клетках, cells — сколько клеток по высоте экрана.
float starLayer(float2 q, float cells, float t, float seed) {
    float2 id = floor(q);
    float2 f = fract(q) - 0.5;
    float2 r = h22(id + seed);
    float d = length(f - (r - 0.5) * 0.7);
    float b = h21(id * 1.7 + seed + 3.1);
    b = b * b * b;
    float tw = 0.6 + 0.4 * sin(t * (0.8 + 2.4 * r.x) + r.y * 40.0);
    float px = uSize.y / cells;
    return b * tw * exp(-d * d * px * px * 0.55);
}
"""

internal const val WALL_TAIL_AGSL = """
half4 main(float2 fc) {
    float2 uv = fc / uSize;
    float aspect = uSize.x / uSize.y;
    // Касание: по фону расходится круг, картинка под ним чуть «проминается», как вода.
    float ring = 0.0;
    if (uTouch.z >= 0.0 && uTouch.z < 1.8) {
        float2 dv = (uv - uTouch.xy) * float2(aspect, 1.0);
        float d = length(dv);
        float life = 1.0 - uTouch.z / 1.8;
        float x = (d - uTouch.z * 0.5) * 16.0;
        ring = exp(-x * x) * life * life;
        uv += dv / max(d, 0.001) * ring * 0.018;
    }
    float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.5);
    float4 s = scene(uv, p, uTime);
    float3 light = max(hueShift(max(s.rgb, 0.0), uHue), 0.0) * uPower;
    // Новое сообщение: снизу вверх проходит мягкая волна света.
    float wave = 0.0;
    if (uPulse >= 0.0 && uPulse < 2.2) {
        float y = 1.05 - uPulse * 0.6;
        float x = (uv.y - y) * 7.0;
        wave = exp(-x * x) * (1.0 - uPulse / 2.2);
    }
    light *= 1.0 + wave * 0.9 + ring * 1.6;
    // Под шапкой и полем ввода фон спокойнее.
    float calm = mix(0.55, 1.0, sstep(0.0, 0.14, uv.y)) * mix(0.6, 1.0, sstep(1.0, 0.86, uv.y));
    light *= calm;
    float3 col;
    if (uDark > 0.5) {
        col = uBg * s.a + light + uInk * (wave * 0.08 + ring * 0.22);
        col = col / (1.0 + max(col - 0.8, 0.0) * 0.7);
    } else {
        float m = max(light.r, max(light.g, light.b));
        float lo = min(light.r, min(light.g, light.b));
        float3 hue = light / max(m, 0.0001);
        float sat = 1.0 - lo / max(m, 0.0001);
        float3 tint = mix(uInk, hue, sstep(0.0, 0.45, sat));
        float a = (1.0 - exp(-m * 1.5)) * 0.78;
        col = uBg * mix(float3(1.0, 1.0, 1.0), tint, a) * mix(0.9, 1.0, s.a);
        col *= 1.0 - (wave * 0.06 + ring * 0.12);
    }
    col += (h21(fc + fract(uTime) * 61.0) - 0.5) / 200.0;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val WALL_AURORA_AGSL = """
// Северное сияние: занавесы света с лучами над звёздным небом.
float4 scene(float2 uv, float2 p, float t) {
    float3 col = float3(0.0, 0.0, 0.0);
    float2 sp = p + uTilt * 0.012;
    float st = starLayer(sp * 52.0, 52.0, t, 11.0) * 0.8 + starLayer(sp * 24.0 + 5.0, 24.0, t, 47.0) * 1.4;
    float veil = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float x = p.x * (1.3 + fi * 0.5) + fi * 3.7 + uTilt.x * 0.06 * (fi + 1.0);
        float n = fbm(float2(x * 1.1 + t * 0.03 * (1.0 + fi * 0.5), t * 0.04 + fi * 7.3));
        float base = 0.40 + fi * 0.12 + (n - 0.5) * 0.75;
        float d = base - uv.y;
        // Лучи: тонкие вертикальные складки, которые бегут вдоль занавеса.
        float fold = vnoise(float2(x * 22.0 + n * 14.0 + t * 0.12, fi * 5.0));
        float fine = vnoise(float2(x * 70.0 + n * 30.0 - t * 0.2, fi * 9.0 + 2.0));
        float rays = 0.25 + 0.75 * pow(fold, 1.6) + 0.25 * fine;
        float len = 2.3 - fi * 0.35 + (1.0 - fold) * 2.2;
        float body = sstep(-0.006, 0.012, d) * exp(-max(d, 0.0) * len) * rays;
        float edge = exp(-abs(d) * 70.0) * (0.35 + 0.65 * fold);
        float3 c = mix(float3(0.08, 1.0, 0.50), float3(0.16, 0.62, 1.0), sstep(0.02, 0.30, d));
        c = mix(c, float3(0.72, 0.22, 1.0), sstep(0.22, 0.62, d));
        float k = 0.80 - fi * 0.16;
        col += c * (body * 0.78 + edge * 0.50) * k;
        veil += (body + edge) * k;
    }
    col += float3(0.75, 0.82, 1.0) * st * (1.0 - clamp(veil * 0.9, 0.0, 0.85));
    // Зарево у земли.
    col += float3(0.04, 0.17, 0.13) * sstep(0.5, 1.05, uv.y) * 0.6;
    return float4(col, 1.0);
}
"""

private const val WALL_COSMOS_AGSL = """
// Космос: туманность, три слоя звёзд с глубиной и падающая звезда раз в несколько секунд.
float4 scene(float2 uv, float2 p, float t) {
    float3 col = float3(0.0, 0.0, 0.0);
    float2 q = p * 1.35 + float2(t * 0.006, -t * 0.004) + uTilt * 0.02;
    float2 w = float2(fbm(q + float2(0.0, t * 0.01)), fbm(q + float2(5.2, 1.3) - t * 0.008));
    float f = fbm(q * 1.2 + w * 2.6);
    float g = fbm(q * 0.8 - w * 1.7 + 4.0);
    float cloud = sstep(0.36, 0.82, f);
    float3 neb = mix(float3(0.42, 0.16, 0.90), float3(0.08, 0.42, 0.95), sstep(0.32, 0.70, g));
    neb = mix(neb, float3(1.0, 0.36, 0.60), sstep(0.56, 0.86, f) * 0.75);
    col += neb * cloud * (0.42 + 0.5 * sstep(0.5, 0.9, f));
    col += float3(0.14, 0.10, 0.36) * sstep(0.2, 0.75, g) * 0.3;
    float dust = 1.0 - 0.5 * sstep(0.5, 0.9, fbm(q * 2.1 + 9.0)) * cloud;
    float2 i1 = floor(p * 64.0 + uTilt * 0.5);
    float s1 = starLayer(p * 64.0 + uTilt * 0.5, 64.0, t, 3.0);
    float s2 = starLayer(p * 34.0 + uTilt * 1.2 + 7.0, 34.0, t, 19.0);
    float s3 = starLayer(p * 13.0 + uTilt * 2.4 + 3.0, 13.0, t, 71.0);
    float warm = h21(floor(p * 34.0 + uTilt * 1.2 + 7.0) + 5.0);
    col += float3(0.70, 0.80, 1.0) * s1 * 0.75 * dust;
    col += mix(float3(0.80, 0.86, 1.0), float3(1.0, 0.80, 0.62), sstep(0.6, 0.9, warm)) * s2 * 1.3 * dust;
    col += float3(1.0, 0.90, 0.80) * s3 * 2.6;
    float slot = floor(t / 7.0);
    float lt = fract(t / 7.0) * 7.0;
    if (lt < 1.1) {
        float2 r = h22(float2(slot, slot * 1.7 + 2.0));
        float2 a = float2((r.x - 0.5) * 0.5, -0.42 + r.y * 0.35);
        float2 dir = normalize(float2(0.55 + r.x * 0.3, 0.42));
        float k = lt / 1.1;
        float2 head = a + dir * k * 0.75;
        float2 rel = p - head;
        float along = dot(rel, -dir);
        float across = abs(dot(rel, float2(-dir.y, dir.x)));
        float tail = sstep(0.0, 0.004, along) * exp(-along * 9.0) * exp(-across * across * 60000.0);
        float glow = exp(-dot(rel, rel) * 9000.0);
        col += float3(0.9, 0.95, 1.0) * (tail * 0.9 + glow * 1.4) * sin(k * 3.14159);
    }
    return float4(col, 1.0);
}
"""

private const val WALL_GALAXY_AGSL = """
// Галактика: спиральные рукава медленно вращаются вокруг яркого ядра.
float4 scene(float2 uv, float2 p, float t) {
    float3 col = float3(0.0, 0.0, 0.0);
    float2 c = p - float2(0.0, -0.06) + uTilt * 0.03;
    float2 g = rot(c, 0.5);
    g.y *= 1.9;
    float r = length(g);
    float a = atan(g.y, g.x);
    float sw = a * 2.0 - log(r + 0.02) * 4.2 - t * 0.07;
    float n = fbm(rot(g, -log(r + 0.02) * 2.1 - t * 0.035) * 7.0 + 3.0);
    float arm = pow(0.5 + 0.5 * cos(sw + (n - 0.5) * 2.4), 2.4);
    float disk = exp(-r * 3.3);
    float dustLane = 1.0 - 0.55 * pow(0.5 + 0.5 * cos(sw + 1.25 + (n - 0.5) * 2.0), 6.0);
    float3 armCol = mix(float3(0.30, 0.42, 1.0), float3(0.86, 0.36, 0.78), sstep(0.05, 0.42, r));
    col += armCol * arm * disk * dustLane * 1.5 * (0.6 + 0.8 * n);
    col += float3(1.0, 0.82, 0.60) * exp(-r * 13.0) * 1.3;
    col += float3(1.0, 0.92, 0.80) * exp(-r * 40.0) * 1.2;
    col += float3(0.22, 0.16, 0.42) * exp(-r * 1.9) * 0.45;
    // Звёзды диска гуще в рукавах.
    float2 sg = rot(g, -t * 0.012);
    float sd = starLayer(sg * 70.0, 70.0 * 1.4, t, 5.0) + starLayer(sg * 34.0 + 3.0, 34.0 * 1.4, t, 31.0) * 1.4;
    col += float3(0.85, 0.88, 1.0) * sd * (0.35 + 1.8 * arm * disk);
    float2 bp = p + uTilt * 0.01;
    col += float3(0.7, 0.78, 1.0) * starLayer(bp * 50.0 + 9.0, 50.0, t, 91.0) * 0.7;
    return float4(col, 1.0);
}
"""

private const val WALL_SEA_AGSL = """
// Ночное море: луна, лунная дорожка и волны в несколько слоёв.
float4 scene(float2 uv, float2 p, float t) {
    float hz = 0.46;
    float2 moon = float2(0.10 + uTilt.x * 0.02, -0.25);
    float md = length(p - moon);
    float3 col = float3(0.03, 0.06, 0.16) * sstep(-0.1, hz, uv.y) * 1.2;
    col += float3(0.20, 0.30, 0.55) * exp(-md * 5.0) * 0.55;
    col += float3(0.95, 0.93, 0.82) * sstep(0.062, 0.056, md) * 0.95;
    col -= float3(0.25, 0.25, 0.22) * sstep(0.062, 0.056, md) * sstep(0.45, 0.75, fbm(p * 30.0)) * 0.6;
    float st = starLayer((p + uTilt * 0.01) * 48.0, 48.0, t, 7.0) + starLayer(p * 22.0 + 4.0, 22.0, t, 53.0) * 1.3;
    col += float3(0.8, 0.85, 1.0) * st * sstep(hz, hz - 0.2, uv.y) * 0.8;
    // Облака у горизонта.
    float cl = sstep(0.5, 0.85, fbm(float2(p.x * 2.2 + t * 0.012, uv.y * 9.0))) * sstep(hz, hz - 0.14, uv.y) * sstep(hz - 0.26, hz - 0.1, uv.y);
    col = mix(col, float3(0.10, 0.14, 0.30), cl * 0.6);
    if (uv.y > hz - 0.01) {
        float3 water = float3(0.0, 0.0, 0.0);
        float lit = 0.0;
        for (int i = 0; i < 6; i++) {
            float fi = float(i);
            float depth = fi / 5.0;
            float y0 = hz + 0.012 + depth * depth * 0.46 + depth * 0.05;
            float amp = 0.004 + depth * 0.02;
            float fr = 34.0 - depth * 24.0;
            float sp = 0.5 + depth * 0.7;
            float x = p.x + uTilt.x * 0.02 * (1.0 + depth * 3.0);
            float wv = sin(x * fr + t * sp + fi * 1.9) * amp + sin(x * fr * 0.47 - t * sp * 0.6 + fi * 4.1) * amp * 0.8;
            float y = y0 + wv;
            float m = sstep(y - 0.003, y + 0.003, uv.y);
            float3 body = mix(float3(0.04, 0.10, 0.26), float3(0.02, 0.05, 0.15), depth);
            body += float3(0.05, 0.12, 0.22) * exp(-(uv.y - y) * (26.0 - depth * 14.0));
            float crest = exp(-abs(uv.y - y) * (260.0 - depth * 150.0));
            // Лунная дорожка: блики на гребнях под луной.
            float lw = 0.045 + depth * 0.13;
            float lx = (p.x - moon.x) / lw;
            float lane = exp(-lx * lx);
            float glit = sstep(0.60, 0.85, vnoise(float2(x * (70.0 - depth * 40.0) + sin(t * 0.6 + fi) * 1.5, (uv.y - y) * (520.0 - depth * 300.0) - t * (1.2 + fi * 0.3))));
            body += float3(0.80, 0.85, 0.95) * crest * (0.10 + lane * 0.8);
            body += float3(0.85, 0.88, 0.95) * lane * glit * exp(-(uv.y - y) * (14.0 - depth * 6.0)) * 0.55;
            body += float3(0.30, 0.36, 0.50) * lane * exp(-(uv.y - y) * 10.0) * 0.25;
            water = mix(water, body, m);
            lit = max(lit, m);
        }
        col = mix(col, water, lit);
    }
    return float4(max(col, 0.0), 1.0);
}
"""

private const val WALL_DEEP_AGSL = """
// Под водой: солнечные блики на глубине, лучи сверху и пузырьки.
float caustic(float2 p, float t) {
    float2 i = p;
    float c = 1.0;
    float inten = 0.005;
    for (int n = 0; n < 4; n++) {
        float tt = t * (1.0 - 3.5 / float(n + 1));
        i = p + float2(cos(tt - i.x) + sin(tt + i.y), sin(tt - i.y) + cos(tt + i.x));
        float2 den = float2(sin(i.x + tt) / inten, cos(i.y + tt) / inten);
        den = sign(den) * max(abs(den), 0.001);
        c += 1.0 / length(float2(p.x / den.x, p.y / den.y));
    }
    c /= 4.0;
    c = 1.17 - pow(c, 1.4);
    return pow(abs(c), 8.0);
}
float bubbles(float2 p, float t, float scale, float seed, float speed) {
    float2 q = p * scale + float2(0.0, t * speed);
    float2 id = floor(q);
    float2 f = fract(q) - 0.5;
    float2 r = h22(id + seed);
    float keep = step(0.55, h21(id * 1.3 + seed + 2.0));
    float rad = 0.05 + 0.09 * r.x;
    float2 c = (r - 0.5) * 0.5 + float2(sin(t * (1.0 + r.y) + r.x * 20.0) * 0.12, 0.0);
    float d = length(f - c);
    float rim = sstep(rad * 0.55, rad * 0.95, d) * sstep(rad * 1.05, rad * 0.95, d);
    float spec = exp(-dot(f - c - float2(-rad * 0.35, -rad * 0.35), f - c - float2(-rad * 0.35, -rad * 0.35)) / (rad * rad * 0.05));
    return (rim * 0.7 + spec * 0.6) * keep;
}
float4 scene(float2 uv, float2 p, float t) {
    float depth = sstep(-0.5, 0.55, p.y);
    float3 col = mix(float3(0.04, 0.34, 0.46), float3(0.01, 0.07, 0.20), depth) * 0.75;
    float2 cp = (p + uTilt * 0.02) * float2(5.5, 7.5) + float2(0.0, t * 0.05);
    float ca = caustic(mod(cp, 6.28318) - 250.0, t * 0.45);
    col += float3(0.35, 0.95, 0.90) * ca * (0.42 - depth * 0.26);
    // Лучи сверху.
    float ang = (p.x + uTilt.x * 0.04) * 2.4 + (p.y + 0.5) * 0.9;
    float rays = pow(vnoise(float2(ang * 3.0 + t * 0.06, 1.0)) * 0.6 + vnoise(float2(ang * 7.0 - t * 0.09, 5.0)) * 0.4, 2.2);
    col += float3(0.35, 0.80, 0.85) * rays * (1.0 - depth) * 0.55;
    float b = bubbles(p + uTilt * 0.02, t, 9.0, 5.0, 0.5) * 0.5 + bubbles(p + uTilt * 0.05 + 3.0, t, 5.0, 31.0, 0.8) * 0.8;
    col += float3(0.75, 0.95, 1.0) * b * 0.55;
    return float4(col, 1.0);
}
"""

private const val WALL_SNOW_AGSL = """
// Снегопад: хлопья в несколько слоёв, дальние мельче и медленнее, внизу сугробы.
float flakes(float2 p, float t, float scale, float seed, float speed, float size) {
    float2 q = p * scale + float2(sin(t * 0.2 + seed) * 0.6, -t * speed);
    float2 id = floor(q);
    float2 f = fract(q) - 0.5;
    float2 r = h22(id + seed);
    float2 c = (r - 0.5) * 0.5 + float2(sin(t * (0.6 + r.x) + r.y * 30.0) * 0.12, 0.0);
    float d = length(f - c);
    float keep = step(0.25, h21(id * 1.3 + seed + 2.0));
    return sstep(size, size * 0.2, d) * keep * (0.6 + 0.4 * r.y);
}
float4 scene(float2 uv, float2 p, float t) {
    float3 col = float3(0.05, 0.09, 0.20) * sstep(-0.6, 0.5, p.y);
    col += float3(0.16, 0.22, 0.42) * exp(-length(p - float2(-0.1, -0.45)) * 3.0) * 0.5;
    float s = 0.0;
    s += flakes(p + uTilt * 0.008, t, 30.0, 3.0, 1.0, 0.09) * 0.30;
    s += flakes(p + uTilt * 0.015, t, 21.0, 13.0, 1.3, 0.10) * 0.45;
    s += flakes(p + uTilt * 0.03, t, 14.0, 29.0, 1.6, 0.11) * 0.65;
    s += flakes(p + uTilt * 0.05, t, 9.0, 47.0, 1.9, 0.12) * 0.90;
    s += flakes(p + uTilt * 0.08, t, 5.5, 71.0, 2.2, 0.11) * 1.0;
    // Сугробы.
    float h1 = 0.36 + sin(p.x * 5.0 + 1.0) * 0.03 + sin(p.x * 11.0) * 0.012;
    float h2 = 0.42 + sin(p.x * 4.0 + 3.0) * 0.035;
    float m1 = sstep(h1 - 0.004, h1 + 0.004, p.y);
    float m2 = sstep(h2 - 0.004, h2 + 0.004, p.y);
    col = mix(col, float3(0.16, 0.22, 0.38) + float3(0.20, 0.24, 0.30) * exp(-(p.y - h1) * 18.0), m1);
    col = mix(col, float3(0.24, 0.30, 0.46) + float3(0.22, 0.24, 0.28) * exp(-(p.y - h2) * 14.0), m2);
    col += float3(0.92, 0.95, 1.0) * s;
    return float4(col, 1.0);
}
"""

private const val WALL_STORM_AGSL = """
// Гроза: тяжёлые тучи, косой дождь и молния раз в несколько секунд.
float4 scene(float2 uv, float2 p, float t) {
    float2 q = float2(p.x * 1.6 + t * 0.02 + uTilt.x * 0.03, p.y * 2.4);
    float cl = fbm(q + float2(fbm2(q * 1.5 + t * 0.015) * 0.8, t * 0.01));
    float cl2 = fbm(q * 2.3 + 7.0 - float2(t * 0.03, 0.0));
    float cover = sstep(0.85, -0.25, p.y + (cl - 0.5) * 0.5);
    float lump = sstep(0.28, 0.78, cl);
    float3 col = mix(float3(0.035, 0.05, 0.11), float3(0.22, 0.27, 0.42), lump * (0.45 + 0.55 * cl2)) * (0.5 + 0.5 * cover);
    // Светлая кайма у края туч и лиловый отсвет в глубине.
    col += float3(0.20, 0.24, 0.40) * sstep(0.62, 0.9, cl) * cover * 0.5;
    col += float3(0.14, 0.08, 0.26) * sstep(0.45, 0.9, cl2) * cover * 0.6;
    // Молния.
    float period = 6.5;
    float slot = floor(t / period);
    float lt = t - slot * period;
    float2 r = h22(float2(slot * 1.3 + 4.0, slot * 0.7));
    float dur = 0.55;
    if (lt < dur) {
        float k = lt / dur;
        float flick = (0.55 + 0.45 * sin(lt * 70.0)) * (1.0 - k) * sstep(0.0, 0.05, k);
        float bx = (r.x - 0.5) * 0.3;
        float yy = p.y + 0.5;
        float path = bx + (fbm(float2(yy * 5.0, slot * 3.0)) - 0.5) * 0.22 * (0.4 + yy) + (vnoise(float2(yy * 40.0, slot)) - 0.5) * 0.02;
        float dx = abs(p.x - path);
        float reach = sstep(0.62 + r.y * 0.25, 0.55 + r.y * 0.25, yy);
        float bolt = (exp(-dx * 260.0) + exp(-dx * 38.0) * 0.28) * reach;
        // Ветка.
        float path2 = path + (yy - 0.25) * (r.y - 0.4) * 0.9 + (vnoise(float2(yy * 30.0, slot + 9.0)) - 0.5) * 0.03;
        float dx2 = abs(p.x - path2);
        bolt += exp(-dx2 * 320.0) * 0.5 * sstep(0.25, 0.27, yy) * sstep(0.52, 0.45, yy);
        col += float3(0.82, 0.85, 1.0) * bolt * flick * 1.5;
        // Вспышка подсвечивает тучи вокруг.
        float nearLit = exp(-length(float2(p.x - bx, (yy - 0.2) * 0.6)) * 3.2);
        col += float3(0.50, 0.55, 0.85) * (0.25 + 0.75 * cl) * nearLit * flick * 0.85;
    }
    // Дождь.
    float rain = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float sc = 26.0 + fi * 16.0;
        float2 rp = float2((p.x + p.y * 0.18 + uTilt.x * 0.02 * (fi + 1.0)) * sc, p.y * (2.2 + fi * 0.8) - t * (2.6 + fi * 0.9));
        float2 id = floor(rp);
        float2 f = fract(rp);
        float on = step(0.72, h21(id + fi * 17.0));
        float xx = abs(f.x - 0.5 - (h21(id + 3.0) - 0.5) * 0.5);
        rain += on * sstep(0.10, 0.0, xx) * sstep(0.0, 0.25, f.y) * sstep(1.0, 0.55, f.y) * (0.5 + fi * 0.2);
    }
    col += float3(0.50, 0.58, 0.80) * rain * 0.22;
    return float4(col, 1.0);
}
"""

private const val WALL_FIRE_AGSL = """
// Костёр: языки пламени снизу и искры, улетающие вверх.
float embers(float2 p, float t, float scale, float seed, float speed) {
    float2 q = p * scale + float2(0.0, t * speed);
    float2 id = floor(q);
    float2 f = fract(q) - 0.5;
    float2 r = h22(id + seed);
    float keep = step(0.5, h21(id * 1.3 + seed + 2.0));
    float2 c = (r - 0.5) * 0.6 + float2(sin(t * (1.4 + r.x * 2.0) + r.y * 30.0) * 0.22, 0.0);
    float d = length(f - c);
    float flick = 0.5 + 0.5 * sin(t * (5.0 + r.x * 6.0) + r.y * 50.0);
    return (exp(-d * d * 420.0) + exp(-d * 16.0) * 0.08) * keep * flick;
}
float4 scene(float2 uv, float2 p, float t) {
    float h = 1.0 - uv.y;
    float x = p.x + uTilt.x * 0.02;
    // Пламя: шум, который едет вверх и вытягивается в языки.
    float2 q = float2(x * 5.0, h * 2.2 - t * 0.9);
    float warp = fbm2(float2(x * 3.0, h * 1.5 - t * 0.5)) - 0.5;
    float n = fbm(q + float2(warp * 1.6, 0.0));
    float n2 = vnoise(float2(x * 13.0 + warp * 4.0, h * 5.0 - t * 2.2));
    float tongue = n * 0.85 + n2 * 0.22;
    float heat = tongue * (1.45 - h * 2.6) + 0.42 - h * 1.25;
    heat += 0.10 * sin(x * 9.0 + t * 0.8) * (1.0 - h);
    heat = min(heat, 0.92);
    float fl = sstep(0.18, 0.62, heat);
    float3 fcol = mix(float3(0.55, 0.03, 0.02), float3(1.0, 0.28, 0.03), sstep(0.18, 0.45, heat));
    fcol = mix(fcol, float3(1.0, 0.60, 0.08), sstep(0.42, 0.74, heat));
    fcol = mix(fcol, float3(1.0, 0.84, 0.36), sstep(0.74, 0.92, heat));
    float3 col = fcol * fl * (0.62 + 0.38 * tongue) * (1.0 + 0.5 * sstep(0.25, 0.0, h));
    // Тёплый свет от огня на всём фоне.
    float flick = 0.85 + 0.15 * vnoise(float2(t * 3.0, 1.0));
    col += float3(0.50, 0.13, 0.03) * exp(-h * 3.0) * 0.5 * flick * (1.0 - fl);
    col += float3(0.20, 0.03, 0.05) * exp(-h * 0.9) * 0.3;
    // Искры: чем выше, тем тусклее.
    float e = embers(p + uTilt * 0.02, t, 9.0, 3.0, 1.1) + embers(p + uTilt * 0.04 + 3.0, t, 14.0, 21.0, 1.5) * 0.8 + embers(p + uTilt * 0.06 + 7.0, t, 5.5, 53.0, 0.8) * 1.2;
    col += float3(1.0, 0.55, 0.15) * e * exp(-h * 1.7) * 1.5;
    return float4(col, 1.0);
}
"""

private const val WALL_LAVA_AGSL = """
// Лава-лампа: тёплые капли плавают, сливаются и расходятся.
float4 scene(float2 uv, float2 p, float t) {
    float f = 0.0;
    float tt = t * 0.2;
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        float ph = fi * 2.399;
        float r = 0.040 + 0.040 * h11(fi + 4.0);
        float2 c = float2(
            sin(tt * (0.5 + 0.11 * fi) + ph) * 0.15 + sin(tt * 0.31 + fi) * 0.04,
            sin(tt * (0.33 + 0.06 * fi) + ph * 1.7) * 0.46
        );
        c += uTilt * 0.02;
        float2 d = p - c;
        f += r * r / (dot(d, d) + 0.0002);
    }
    // Лужицы снизу и сверху, из которых капли выходят.
    float lowY = 0.535 + sin(p.x * 7.0 + t * 0.35) * 0.012 - p.y;
    float topY = 0.535 + sin(p.x * 6.0 - t * 0.3) * 0.010 + p.y;
    f += 0.0030 / (lowY * lowY + 0.0010) + 0.0026 / (topY * topY + 0.0010);
    float m = sstep(0.96, 1.16, f);
    float core = sstep(1.0, 3.4, f);
    float3 hot = mix(float3(1.0, 0.14, 0.40), float3(1.0, 0.52, 0.10), sstep(-0.3, 0.5, p.y));
    hot = mix(hot, float3(0.62, 0.20, 1.0), sstep(-0.05, -0.5, p.y) * 0.85);
    float g = sstep(0.12, 1.0, f);
    float3 col = hot * (0.26 * g * g + 0.16 * m + 0.42 * core) + float3(1.0, 0.85, 0.6) * core * core * 0.14;
    return float4(col, 1.0);
}
"""

private const val WALL_LIGHTS_AGSL = """
// Огоньки: мягкие размытые круги света плывут вверх, между ними мигают светлячки.
float3 bokehLayer(float2 p, float t, float scale, float seed, float speed) {
    float2 q = p * scale + float2(0.0, t * speed);
    float2 id = floor(q);
    float2 f = fract(q) - 0.5;
    float3 col = float3(0.0, 0.0, 0.0);
    // Хватает четырёх ближайших клеток: фигура из дальних сюда не дотягивается.
    float2 sg = step(0.0, f) * 2.0 - 1.0;
    for (int j = 0; j <= 1; j++) {
        for (int i = 0; i <= 1; i++) {
            float2 o = float2(float(i), float(j)) * sg;
            float2 cid = id + o;
            float2 r = h22(cid + seed);
            float keep = step(0.45, h21(cid * 1.3 + seed + 9.0));
            float rad = 0.22 + 0.26 * r.x;
            float2 c = o + (r - 0.5) * 0.7 + float2(sin(t * 0.3 + r.y * 20.0) * 0.10, 0.0);
            float d = length(f - c);
            float disc = sstep(rad, rad * 0.86, d);
            float rim = sstep(rad * 0.62, rad * 0.98, d) * disc;
            float hsel = h21(cid + seed * 3.0 + 1.0);
            float3 tint = mix(float3(1.0, 0.72, 0.30), float3(1.0, 0.38, 0.52), sstep(0.3, 0.7, hsel));
            tint = mix(tint, float3(0.40, 0.72, 1.0), sstep(0.82, 0.95, hsel));
            float pulse = 0.65 + 0.35 * sin(t * (0.4 + r.x) + r.y * 30.0);
            col += tint * (disc * 0.55 + rim * 0.45) * keep * pulse;
        }
    }
    return col;
}
float4 scene(float2 uv, float2 p, float t) {
    float3 col = float3(0.10, 0.05, 0.12) * sstep(-0.6, 0.6, p.y) * 0.5;
    col += bokehLayer(p + uTilt * 0.01, t, 2.6, 3.0, 0.035) * 0.16;
    col += bokehLayer(p + uTilt * 0.025 + 3.0, t, 4.2, 17.0, 0.06) * 0.20;
    col += bokehLayer(p + uTilt * 0.05 + 7.0, t, 7.5, 41.0, 0.11) * 0.24;
    // Светлячки.
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        float2 r = h22(float2(fi, fi * 2.7 + 1.0));
        float2 c = float2(
            (r.x - 0.5) * 0.5 + sin(t * (0.21 + 0.05 * fi) + fi * 2.1) * 0.12,
            (r.y - 0.5) * 0.9 + cos(t * (0.17 + 0.04 * fi) + fi * 1.3) * 0.10
        );
        float blink = pow(0.5 + 0.5 * sin(t * (0.9 + r.x) + fi * 4.0), 3.0);
        float d = length(p - c + uTilt * 0.06);
        col += float3(1.0, 0.92, 0.55) * (exp(-d * d * 30000.0) * 1.6 + exp(-d * 55.0) * 0.22) * blink;
    }
    return float4(col, 1.0);
}
"""

private const val WALL_HEARTS_AGSL = """
// Сердечки: плывут вверх в три слоя, ближние крупнее и ярче.
float dot2(float2 v) { return dot(v, v); }
float sdHeart(float2 q) {
    q.x = abs(q.x);
    q.y = 0.5 - q.y;
    if (q.y + q.x > 1.0) {
        return sqrt(dot2(q - float2(0.25, 0.75))) - 0.35355;
    }
    return sqrt(min(dot2(q - float2(0.0, 1.0)), dot2(q - 0.5 * max(q.x + q.y, 0.0)))) * sign(q.x - q.y);
}
float3 heartLayer(float2 p, float t, float scale, float seed, float speed, float blur) {
    float2 q = p * scale + float2(0.0, t * speed);
    float2 id = floor(q);
    float2 f = fract(q) - 0.5;
    float3 col = float3(0.0, 0.0, 0.0);
    // Хватает четырёх ближайших клеток: фигура из дальних сюда не дотягивается.
    float2 sg = step(0.0, f) * 2.0 - 1.0;
    for (int j = 0; j <= 1; j++) {
        for (int i = 0; i <= 1; i++) {
            float2 o = float2(float(i), float(j)) * sg;
            float2 cid = id + o;
            float2 r = h22(cid + seed);
            float keep = step(0.5, h21(cid * 1.7 + seed + 5.0));
            float size = 0.20 + 0.20 * r.y;
            float2 c = o + (r - 0.5) * 0.7 + float2(sin(t * (0.5 + r.x * 0.5) + r.y * 30.0) * 0.10, 0.0);
            float2 h = rot(f - c, sin(t * 0.6 + r.x * 20.0) * 0.25) / size;
            float d = sdHeart(h) * size;
            float fill = sstep(blur, -blur, d);
            float halo = exp(-max(d, 0.0) * 14.0) * 0.35;
            float sel = h21(cid + seed * 2.0 + 3.0);
            float3 tint = mix(float3(1.0, 0.22, 0.42), float3(1.0, 0.45, 0.70), sstep(0.2, 0.6, sel));
            tint = mix(tint, float3(0.72, 0.36, 1.0), sstep(0.78, 0.92, sel));
            float beat = 1.0 + 0.08 * sin(t * 2.2 + r.x * 40.0);
            col += tint * (fill * (0.75 + 0.25 * sstep(0.3, -0.5, h.y)) + halo) * keep * beat;
        }
    }
    return col;
}
float4 scene(float2 uv, float2 p, float t) {
    float3 col = float3(0.16, 0.03, 0.10) * sstep(0.7, -0.5, p.y) * 0.6;
    col += heartLayer(p + uTilt * 0.01, t, 7.0, 5.0, 0.10, 0.10) * 0.16;
    col += heartLayer(p + uTilt * 0.03 + 4.0, t, 4.4, 23.0, 0.16, 0.05) * 0.26;
    col += heartLayer(p + uTilt * 0.06 + 9.0, t, 2.6, 57.0, 0.24, 0.02) * 0.42;
    return float4(col, 1.0);
}
"""

private const val WALL_SAKURA_AGSL = """
// Сакура: лепестки кружатся и падают наискось, дальние размыты.
float3 petalLayer(float2 p, float t, float scale, float seed, float speed, float blur) {
    float2 q = p * scale + float2(t * speed * 0.35, -t * speed);
    float2 id = floor(q);
    float2 f = fract(q) - 0.5;
    float3 col = float3(0.0, 0.0, 0.0);
    // Хватает четырёх ближайших клеток: фигура из дальних сюда не дотягивается.
    float2 sg = step(0.0, f) * 2.0 - 1.0;
    for (int j = 0; j <= 1; j++) {
        for (int i = 0; i <= 1; i++) {
            float2 o = float2(float(i), float(j)) * sg;
            float2 cid = id + o;
            float2 r = h22(cid + seed);
            float keep = step(0.30, h21(cid * 1.7 + seed + 5.0));
            float size = 0.16 + 0.14 * r.y;
            float2 c = o + (r - 0.5) * 0.7 + float2(sin(t * (0.7 + r.x) + r.y * 30.0) * 0.16, 0.0);
            float ang = t * (0.6 + r.x * 0.9) + r.y * 40.0;
            float2 h = rot(f - c, ang) / size;
            // Лепесток кувыркается: ширина меняется, как будто он поворачивается ребром.
            h.x /= 0.35 + 0.65 * abs(cos(t * (0.9 + r.y) + r.x * 30.0));
            float tip = 1.0 - 0.55 * sstep(0.0, 1.0, h.y);
            float d = length(float2(h.x / (0.62 * tip), h.y)) - 1.0;
            // Выемка на кончике.
            d = max(d, 0.16 - length(h - float2(0.0, -1.02)));
            float fill = sstep(blur * 4.0, -blur * 4.0, d);
            float sel = h21(cid + seed * 2.0 + 3.0);
            float3 tint = mix(float3(1.0, 0.58, 0.74), float3(1.0, 0.80, 0.86), sel);
            col += tint * fill * (0.65 + 0.35 * sstep(-1.0, 1.0, h.y)) * keep;
        }
    }
    return col;
}
float4 scene(float2 uv, float2 p, float t) {
    float3 col = float3(0.22, 0.05, 0.16) * sstep(0.8, -0.6, p.y) * 0.55;
    col += float3(0.42, 0.12, 0.30) * exp(-length(p - float2(0.22, -0.5)) * 2.2) * 0.6;
    col += float3(0.30, 0.10, 0.36) * exp(-length(p - float2(-0.2, 0.3)) * 3.0) * 0.35;
    col += petalLayer(p + uTilt * 0.01, t, 8.0, 5.0, 0.16, 0.10) * 0.26;
    col += petalLayer(p + uTilt * 0.03 + 4.0, t, 5.0, 23.0, 0.26, 0.05) * 0.46;
    col += petalLayer(p + uTilt * 0.06 + 9.0, t, 3.0, 57.0, 0.40, 0.02) * 0.74;
    return float4(col, 1.0);
}
"""

private const val WALL_SILK_AGSL = """
// Шёлк: цветные складки медленно перетекают друг в друга.
float4 scene(float2 uv, float2 p, float t) {
    float2 q = p * 1.25 + uTilt * 0.03;
    float tt = t * 0.035;
    float2 a = float2(fbm(q + float2(0.0, tt)), fbm(q + float2(5.2, 1.3) - tt));
    float2 b = float2(fbm(q * 1.1 + a * 2.6 + float2(1.7, 9.2) + tt * 0.7), fbm(q * 1.1 + a * 2.6 + float2(8.3, 2.8) - tt * 0.6));
    float f = fbm(q * 0.9 + b * 2.4);
    float3 c = pal(f * 0.9 + b.x * 0.45 + t * 0.006, float3(0.50, 0.34, 0.62), float3(0.46, 0.30, 0.38), float3(1.0, 1.0, 1.0), float3(0.86, 0.52, 0.20));
    // Складки: светлые гребни там, где поле резко меняется.
    float fold = abs(fract(f * 5.0 + b.y * 1.5) - 0.5) * 2.0;
    float sheen = pow(1.0 - fold, 6.0);
    float shade = 0.40 + 0.55 * sstep(0.25, 0.85, f);
    float3 col = c * shade * 0.72 + float3(1.0, 0.92, 0.96) * sheen * 0.16 * sstep(0.3, 0.7, f);
    return float4(col, 1.0);
}
"""

private const val WALL_CRYSTAL_AGSL = """
// Кристаллы: грани медленно смещаются и переливаются, по рёбрам бежит свет.
float4 scene(float2 uv, float2 p, float t) {
    float2 q = p * 5.2 + uTilt * 0.12;
    float2 id = floor(q);
    float2 f = fract(q);
    float d1 = 8.0;
    float d2 = 8.0;
    float2 best = float2(0.0, 0.0);
    float2 bestRel = float2(0.0, 0.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            float2 o = float2(float(i), float(j));
            float2 r = h22(id + o);
            float2 c = o + 0.5 + 0.38 * sin(t * 0.22 + 6.28318 * r);
            float2 rel = c - f;
            float d = dot(rel, rel);
            if (d < d1) {
                d2 = d1;
                d1 = d;
                best = id + o;
                bestRel = rel;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    float edge = sqrt(d2) - sqrt(d1);
    float cellH = h21(best + 7.0);
    // Грань освещена по-своему: у каждой свой наклон.
    float2 nrm = normalize(h22(best + 3.0) - 0.5 + 0.001);
    float facet = clamp(0.5 + dot(bestRel, nrm) * 0.9, 0.0, 1.0);
    float shimmer = 0.5 + 0.5 * sin(t * 0.5 + cellH * 30.0 + (p.x + p.y) * 3.0);
    float3 a = pal(cellH * 0.30 + (p.y + 0.5) * 0.30 + t * 0.008, float3(0.46, 0.36, 0.66), float3(0.40, 0.30, 0.34), float3(1.0, 1.0, 1.0), float3(0.84, 0.52, 0.22));
    float3 col = a * (0.20 + 0.42 * facet + 0.16 * shimmer * facet);
    float line = sstep(0.07, 0.0, edge);
    float run = 0.5 + 0.5 * sin(t * 0.8 - (p.y * 6.0 + p.x * 3.0));
    col += float3(0.70, 0.85, 1.0) * line * (0.30 + 0.45 * run);
    col += float3(0.9, 0.95, 1.0) * sstep(0.02, 0.0, edge) * 0.25;
    return float4(col, 1.0);
}
"""

private const val WALL_RETRO_AGSL = """
// Ретровейв: неоновое солнце в полоску, горы и бегущая сетка до горизонта.
float4 scene(float2 uv, float2 p, float t) {
    float hz = 0.08;
    float3 col = float3(0.0, 0.0, 0.0);
    float y = p.y;
    if (y < hz) {
        float k = sstep(-0.5, hz, y);
        col = mix(float3(0.05, 0.02, 0.16), float3(0.55, 0.08, 0.50), k * k);
        col += float3(1.0, 0.30, 0.50) * exp(-(hz - y) * 14.0) * 0.45;
        float st = starLayer((p + uTilt * 0.01) * 44.0, 44.0, t, 13.0) + starLayer(p * 20.0 + 3.0, 20.0, t, 37.0) * 1.3;
        col += float3(0.85, 0.8, 1.0) * st * sstep(hz - 0.06, hz - 0.35, y);
        // Солнце.
        float2 sc = float2(uTilt.x * 0.015, hz - 0.13);
        float sd = length(p - sc);
        float sr = 0.165;
        float sy = (p.y - (sc.y - sr)) / (2.0 * sr);
        float band = fract(sy * 9.0 - t * 0.12);
        float cut = step(0.42, sy) * step(band, 0.16 + sy * 0.42);
        float disc = sstep(sr, sr - 0.004, sd) * (1.0 - cut);
        float3 sun = mix(float3(1.0, 0.86, 0.25), float3(1.0, 0.20, 0.55), sstep(0.1, 0.95, sy));
        col = mix(col, sun, disc);
        col += float3(1.0, 0.35, 0.55) * exp(-max(sd - sr, 0.0) * 9.0) * 0.30 * (1.0 - disc);
        // Горы.
        float mx = p.x + uTilt.x * 0.03;
        float ridge = hz - 0.012 - (0.02 + fbm2(float2(mx * 5.0 + 1.3, 2.0)) * 0.10 + vnoise(float2(mx * 19.0, 7.0)) * 0.02) * sstep(0.07, 0.2, abs(mx));
        float mm = sstep(ridge - 0.002, ridge + 0.002, y);
        col = mix(col, float3(0.05, 0.02, 0.12), mm);
        col += float3(0.25, 0.95, 1.0) * exp(-abs(y - ridge) * 260.0) * 0.55 * sstep(0.04, 0.2, abs(mx));
    } else {
        float dy = y - hz + 0.002;
        float z = 0.11 / dy;
        float x = (p.x + uTilt.x * 0.04) * z * 26.0;
        float gz = z * 12.0 - t * 1.6;
        float px = 2.2 / uSize.y;
        float wx = clamp(z * 26.0 * px, 0.0, 0.5);
        float wz = clamp(12.0 * 0.11 / (dy * dy) * px, 0.0, 0.5);
        float lx = sstep(wx, wx * 0.25, abs(fract(x) - 0.5)) * sstep(0.5, 0.2, wx);
        float lz = sstep(wz, wz * 0.25, abs(fract(gz) - 0.5)) * sstep(0.5, 0.2, wz);
        float gx = sstep(wx * 5.0, 0.0, abs(fract(x) - 0.5)) * 0.25;
        float gzz = sstep(wz * 5.0, 0.0, abs(fract(gz) - 0.5)) * 0.25 * sstep(0.5, 0.1, wz);
        float fade = sstep(0.0, 0.05, dy);
        float line = max(lx, lz) + gx + gzz;
        col = float3(0.07, 0.02, 0.16);
        col += mix(float3(1.0, 0.25, 0.85), float3(0.25, 0.9, 1.0), sstep(0.0, 0.40, dy)) * line * (0.35 + 0.65 * fade) * 0.8;
        col += float3(1.0, 0.30, 0.55) * exp(-dy * 16.0) * 0.6;
    }
    return float4(col, 1.0);
}
"""

private const val WALL_CODE_AGSL = """
// Код: столбцы знаков стекают вниз, первый знак в столбце светится.
float glyph(float2 cell, float2 f, float seed) {
    // Знак 4×5: случайные «чёрточки», по краям клетки пусто.
    float2 g = floor(f * float2(5.0, 7.0));
    float inside = step(0.5, g.x) * step(g.x, 4.5) * step(0.5, g.y) * step(g.y, 5.5);
    float on = step(0.48, h21(cell * 7.0 + g + seed * 13.0));
    return on * inside;
}
float4 scene(float2 uv, float2 p, float t) {
    float rows = 46.0;
    float2 q = float2(p.x + uTilt.x * 0.01, uv.y) * rows;
    float2 cell = floor(q);
    float2 f = fract(q);
    float col1 = h11(cell.x * 3.7 + 11.0);
    float speed = 3.5 + col1 * 6.5;
    float span = rows + 22.0;
    float head = fract(t * speed / span + h11(cell.x + 2.0)) * span - 6.0;
    float behind = head - cell.y;
    float trail = sstep(0.0, 0.6, behind) * exp(-max(behind, 0.0) * (0.10 + col1 * 0.10));
    float lead = sstep(1.2, 0.0, abs(behind - 0.5));
    // Второй, редкий поток в том же столбце.
    float head2 = fract(t * speed * 0.6 / span + h11(cell.x + 40.0)) * span - 6.0;
    float b2 = head2 - cell.y;
    trail = max(trail, sstep(0.0, 0.6, b2) * exp(-max(b2, 0.0) * 0.22) * 0.6);
    float change = floor(t * (1.5 + 4.0 * h21(cell + 3.0)) + h21(cell) * 10.0);
    float gl = glyph(cell, f, change);
    float3 c = float3(0.10, 1.0, 0.42) * gl * trail * 0.75;
    c += float3(0.75, 1.0, 0.85) * gl * lead * 0.9;
    c += float3(0.02, 0.22, 0.09) * trail * 0.35;
    return float4(c, 1.0);
}
"""

/** Сцены по названию фона. */
internal val WALLPAPER_SCENES: Map<String, String> = linkedMapOf(
    "aurora" to WALL_AURORA_AGSL,
    "cosmos" to WALL_COSMOS_AGSL,
    "galaxy" to WALL_GALAXY_AGSL,
    "sea" to WALL_SEA_AGSL,
    "deep" to WALL_DEEP_AGSL,
    "snow" to WALL_SNOW_AGSL,
    "storm" to WALL_STORM_AGSL,
    "fire" to WALL_FIRE_AGSL,
    "lava" to WALL_LAVA_AGSL,
    "lights" to WALL_LIGHTS_AGSL,
    "hearts" to WALL_HEARTS_AGSL,
    "sakura" to WALL_SAKURA_AGSL,
    "silk" to WALL_SILK_AGSL,
    "crystal" to WALL_CRYSTAL_AGSL,
    "retro" to WALL_RETRO_AGSL,
    "code" to WALL_CODE_AGSL,
)

/** Полный текст шейдера фона или null, если такого фона нет. */
internal fun wallpaperSource(id: String): String? = WALLPAPER_SCENES[id]?.let { WALL_HEAD_AGSL + it + WALL_TAIL_AGSL }
