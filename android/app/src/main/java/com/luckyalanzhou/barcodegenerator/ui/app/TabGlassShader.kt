package com.luckyalanzhou.barcodegenerator.ui.app

/**
 * Original AGSL lens. The input is the unmodified tab scene, never a previous glass frame.
 * Sampling and material composition produce one output: no duplicate glyphs or bitmap readback.
 */
internal const val TAB_GLASS_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float4 capsule;
uniform float4 optics;
uniform float2 touchPoint;
layout(color) uniform half4 backgroundColor;
layout(color) uniform half4 accentColor;
uniform float4 material;
uniform float edgeWidth;
uniform float surfaceOpacity;

half4 main(float2 p) {
    half4 original = content.eval(p);
    float radius = min(capsule.z, capsule.w);
    float2 local = p - capsule.xy;
    float2 q = abs(local) - (capsule.zw - float2(radius));
    float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float mask = 1.0 - smoothstep(-0.75, 0.75, sd);
    if (mask <= 0.0) {
        return original;
    }

    float spineX = clamp(local.x, -capsule.z + radius, capsule.z - radius);
    float2 surfaceVector = float2(local.x - spineX, local.y);
    float surfaceLength = length(surfaceVector);
    float2 normal = surfaceVector / max(surfaceLength, 0.001);
    float depth = max(-sd, 0.0);
    float edgeLens = 1.0 - smoothstep(0.0, 11.0 * optics.w, depth);
    // Refraction is strongest near the inner edge; the center stays legible.
    float2 displacement = normal * optics.x * edgeLens * mask;
    displacement += local * (0.006 * optics.y) * (1.0 - edgeLens);
    float2 samplePoint = clamp(p - displacement, float2(0.5), resolution - float2(0.5));
    half4 refracted = content.eval(samplePoint);
    half4 sampled = mix(original, refracted, half(mask));
    // With a separate page backdrop this layer only refracts original foreground pixels.
    if (surfaceOpacity <= 0.0) return sampled;

    half3 glassColor = mix(backgroundColor.rgb, half3(1.0), half(material.x));
    glassColor = mix(glassColor, accentColor.rgb, half(material.y));

    float rim = 1.0 - smoothstep(0.0, max(edgeWidth, 0.5), depth);
    float2 lightVector = touchPoint - capsule.xy;
    float2 lightDirection = lightVector / max(length(lightVector), 0.001);
    float contactLight = pow(max(dot(normal, lightDirection), 0.0), mix(14.0, 4.0, clamp(optics.z, 0.0, 1.0))) * optics.y;
    float topLight = max(-normal.y, 0.0);
    float highlight = (topLight * 0.45 + contactLight * 0.65) * material.z;
    // Light stays on the rim, including while dragging: no central hot spot or external glow.
    glassColor += half3(rim * highlight);
    glassColor *= half(1.0 - rim * max(normal.y, 0.0) * material.w);
    // Source-over in premultiplied space. Transparent input must not become an opaque bar,
    // and antialiased glyphs retain their original color instead of acquiring dark fringes.
    half glassAlpha = half(mask * clamp(surfaceOpacity, 0.0, 1.0));
    half remainingAlpha = glassAlpha * (1.0 - sampled.a);
    half alpha = sampled.a + remainingAlpha;
    half3 color = sampled.rgb + clamp(glassColor, half3(0.0), half3(1.0)) * remainingAlpha;
    return half4(color, alpha);
}
"""
