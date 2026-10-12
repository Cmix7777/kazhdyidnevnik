package io.github.cmix7777.kazhdyidnevnik.ui.fx

/**
 * Шейдер склейки двух недель (AGSL, Android 13+). Каждая неделя описана формулой расстояния до краёв
 * скруглённого прямоугольника; две формулы складываются «мягким минимумом», поэтому при сближении между
 * карточками вырастает перемычка, как между каплями. Формулы и числа те же, что в демо.
 * Все координаты в пикселях, начало в левом верхнем углу.
 */
internal const val GLUE_AGSL = """
uniform float uDpr;
uniform float uTime;
uniform float uK;
uniform float uR;
uniform float uFuse;
uniform float uGlow;
uniform float uDark;
uniform float4 uA;
uniform float4 uB;
uniform float4 uW;
uniform float3 uA1;
uniform float3 uA2;
uniform float3 uB1;
uniform float3 uB2;

float sdBox(float2 p, float2 b, float r) {
    r = min(r, min(b.x, b.y));
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float smin(float a, float b, float k) {
    if (k < 0.001) { return min(a, b); }
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

half4 main(float2 p) {
    float dA = sdBox(p - uA.xy, uA.zw, uR);
    float dB = sdBox(p - uB.xy, uB.zw, uR);
    float dS = smin(dA, dB, uK);
    float dW = sdBox(p - uW.xy, uW.zw, uR);
    float bridge = clamp((min(dA, dB) - dS) / max(uK * 0.25, 1.0), 0.0, 1.0) * (1.0 - uFuse);
    float d = mix(dS, dW, uFuse);
    d += sin(p.y * 0.045 / uDpr + uTime * 7.0) * 1.2 * uDpr * bridge;

    float blend = max(uK, 24.0 * uDpr) * 0.9;
    float wA = clamp(0.5 + (dB - dA) / (2.0 * blend), 0.0, 1.0);
    float vy = clamp((p.y - (uW.y - uW.w)) / (2.0 * uW.w), 0.0, 1.0);
    float3 cA = mix(uA1, uA2, vy);
    float3 cB = mix(uB1, uB2, vy);
    float3 edge = mix(cB, cA, wA);
    float3 glassDark = mix(float3(0.110, 0.078, 0.161), float3(0.071, 0.047, 0.106), vy);
    float3 glassLight = mix(float3(1.0, 1.0, 1.0), float3(0.95, 0.93, 0.98), vy);
    float3 glass = mix(glassLight, glassDark, uDark);

    float dOut = max(d, 0.0);
    float glowA = (0.40 * exp(-dOut / (30.0 * uDpr)) + bridge * 0.45 * exp(-dOut / (10.0 * uDpr))) * uGlow;
    float cov = 1.0 - smoothstep(-0.9 * uDpr, 0.9 * uDpr, d);
    float din = max(-d, 0.0);
    float3 fill = glass + edge * 0.20 * exp(-din / (16.0 * uDpr)) + edge * bridge * 0.55 * exp(-din / (34.0 * uDpr));
    float rim = 1.0 - smoothstep(0.0, 1.4 * uDpr, abs(d));
    float shimmer = 0.85 + 0.15 * sin(uTime * 1.3 + p.x * 0.010 / uDpr + p.y * 0.006 / uDpr);

    float3 col = mix(edge, fill, cov) + (edge * 0.9 * shimmer + float3(0.08)) * rim;
    float a = max(max(cov, clamp(glowA, 0.0, 1.0)), rim);
    return half4(half3(min(col, float3(1.0)) * a), half(a));
}
"""
