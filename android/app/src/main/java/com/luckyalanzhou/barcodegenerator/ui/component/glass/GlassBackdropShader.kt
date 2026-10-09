package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** Input is a GPU replay of the raw page in local coordinates, with no foreground controls. */
internal const val GLASS_BACKDROP_SHADER = GLASS_ADAPTIVE_TINT_SHADER + GLASS_LENS_PROFILE_SHADER + """
uniform shader content;
uniform float2 resolution;
uniform float4 bounds;
uniform float4 shape;
uniform float4 contact;
uniform float capsuleMode;
uniform float menuMaterial;
uniform float2 capsuleOptics;
uniform float pixelDensity;
layout(color) uniform half4 surfaceColor;

half4 main(float2 p) {
    float2 halfSize = bounds.zw;
    float radius = min(shape.x, min(halfSize.x, halfSize.y));
    float2 local = p - bounds.xy;
    float2 q = abs(local) - (halfSize - radius);
    float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float mask = 1.0 - smoothstep(-0.75, 0.75, sd);
    if (mask <= 0.0) return half4(0.0);
    float2 edge = local - clamp(local, -halfSize + radius, halfSize - radius);
    float2 normal = edge / max(length(edge), 0.001);
    float depth = max(-sd, 0.0);
    float density = max(pixelDensity, 0.1);
    float lens = capsuleMode > 0.5 ? glassLensProfile(depth, radius, density) :
        1.0 - smoothstep(0.0, max(radius * 0.5, 1.0), depth);
    // A soft, touch-centered wave bends the live backdrop inside the capsule as well
    // as at its rim. It follows the actual contact and never draws outside the mask.
    float2 touchLocal = contact.xy - bounds.xy;
    float2 fromTouch = local - touchLocal;
    float touchDistance = length(fromTouch);
    float touchRadius = radius * mix(0.08, 0.28, clamp(contact.w, 0.0, 1.0));
    float waveWidth = max(radius * 0.24, 1.5);
    float wave = exp(-pow((touchDistance - touchRadius) / waveWidth, 2.0)) * contact.z;
    wave *= capsuleMode > 0.5 ? smoothstep(0.0, density * 2.0, depth) : 1.0;
    float2 touchDirection = fromTouch / max(touchDistance, 0.001);
    float2 opticalDisplacement = normal * shape.z * lens +
        touchDirection * shape.z * 0.65 * wave;
    float2 samplePoint = clamp(p - opticalDisplacement, float2(0.5), resolution - 0.5);
    half3 center = content.eval(samplePoint).rgb;
    half3 scene = center * 0.4;
    // Spatially smoothed environment, including low-blur tabs. No CPU readback or theme switching.
    float2 spread = float2(max(shape.y * 0.35, 2.0), 0.0);
    scene += content.eval(clamp(samplePoint + spread, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(samplePoint - spread, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(samplePoint + spread.yx, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(samplePoint - spread.yx, float2(0.5), resolution - 0.5)).rgb * 0.15;
    // Few local GPU taps provide contrast protection without CPU readback or global theme changes.
    float luminance = dot(float3(scene), float3(0.2126, 0.7152, 0.0722));
    float targetLuminance = dot(float3(surfaceColor.rgb), float3(0.2126, 0.7152, 0.0722));
    float detail = smoothstep(0.015, 0.35, length(float3(center - scene)));
    float protection = smoothstep(0.12, 0.75, abs(luminance - targetLuminance)) * 0.24 + detail * 0.10;
    float opacity = clamp(shape.w + protection, 0.0, 1.0);
    // Menus contain labels: quiet the interior without making the rim an opaque slab.
    // Capsules keep their existing material; this protection must not tint the whole tab bar.
    float interior = smoothstep(1.5, 12.0, depth) * (1.0 - capsuleMode);
    // 新菜单采用自己的厚度参数；原有 Tab/结果页控件保持原路径，避免材质迁移影响其他控件。
    float interiorOpacity = mix(mix(0.92, 0.94, smoothstep(0.15, 0.75, targetLuminance)), shape.w, menuMaterial);
    opacity = max(opacity, mix(shape.w, interiorOpacity, interior));
    half3 materialTint = capsuleMode > 0.5 ? glassAdaptiveTint(surfaceColor.rgb, scene, opacity) : surfaceColor.rgb;
    half3 color = mix(scene, materialTint, half(opacity));
    // A fine adaptive edge keeps the glass legible on both a white canvas and
    // a black one without painting a broad halo around the control.
    float edgeLine = capsuleMode * (1.0 - smoothstep(0.15, 1.25, depth));
    float lightSurface = smoothstep(0.15, 0.75, targetLuminance);
    half3 edgeTint = mix(half3(0.52, 0.56, 0.64), half3(0.45, 0.48, 0.54), half(lightSurface));
    float edgeAlpha = edgeLine * mix(0.17, 0.075, lightSurface);
    color = mix(color, edgeTint, half(edgeAlpha));
    // Text/detail crossing a small control strengthens only its inner edge separation.
    float separation = capsuleMode * (1.0 - smoothstep(1.0, 5.0, depth)) * detail * 0.045;
    color *= half(1.0 - separation);
    // Small-control optical bevel: geometric, environment-aware and inside the mask.
    // GPU controls own this once; compatible Canvas strokes are not stacked on it.
    if (capsuleMode > 0.5 && shape.w < 0.999) {
        float2 touchLight = contact.xy - bounds.xy;
        float2 lightDirection = normalize(float2(-0.35, -1.0) +
            touchLight / max(length(touchLight), 1.0) * contact.z * 0.45);
        float facing = max(dot(normal, lightDirection), 0.0);
        float opposite = max(-dot(normal, lightDirection), 0.0);
        float crest = exp(-depth / 0.85);
        float bevel = exp(-pow((depth - density * 1.25) / max(density * 1.0, 1.0), 2.0));
        float reflection = crest * pow(facing, 3.0) * mix(0.34, 0.70, lightSurface) +
            bevel * facing * mix(0.075, 0.15, lightSurface);
        float innerShade = bevel * opposite * mix(0.055, 0.10, lightSurface);
        innerShade += crest * (1.0 - facing * facing) * mix(0.015, 0.045, lightSurface);
        // Soft counterreflection defines lower glass thickness, not an external neon halo.
        reflection += crest * opposite * mix(0.09, 0.06, lightSurface);
        half3 environmentalLight = clamp(scene - half3(luminance), half3(-0.12), half3(0.12));
        color = mix(color, half3(1.0), half(clamp(reflection, 0.0, 0.72)));
        color *= half(1.0 - innerShade);
        color += environmentalLight * half(bevel * facing * 0.08);
    }
    // Capsule-only, movement-only edge optics. Never sample/recolor the foreground atlas.
    // Two extra taps are confined to the inner edge band; no frame history or CPU readback.
    float capsuleEdge = capsuleMode * (1.0 - smoothstep(0.0, 2.0, depth)) *
        smoothstep(0.0, 0.65, depth);
    if (capsuleEdge > 0.001 && capsuleOptics.x > 0.001) {
        float2 split = normal * capsuleOptics.x;
        half3 redSide = content.eval(clamp(samplePoint + split, float2(0.5), resolution - 0.5)).rgb;
        half3 blueSide = content.eval(clamp(samplePoint - split, float2(0.5), resolution - 0.5)).rgb;
        half3 spectrum = half3(redSide.r, center.g, blueSide.b);
        color += (spectrum - center) * half(capsuleEdge * (1.0 - opacity) * 0.65);
        // Bounded local environmental color, not Add/Color Dodge or an outside neon glow.
        float darkSurface = 1.0 - smoothstep(0.05, 0.35, targetLuminance);
        color += clamp(scene - half3(luminance), half3(-0.35), half3(0.35)) *
            half(capsuleEdge * capsuleOptics.y * darkSurface);
    }
    // Tab rim is drawn once by TabGlassSurface; only menu backgrounds own their rim here.
    float rim = (1.0 - smoothstep(0.0, 1.5, depth)) * (1.0 - capsuleMode) * (1.0 - menuMaterial);
    float2 lightVector = contact.xy - bounds.xy;
    float2 direction = lightVector / max(length(lightVector), 0.001);
    float contactLight = pow(max(dot(normal, direction), 0.0), mix(14.0, 4.0, clamp(contact.w, 0.0, 1.0))) * contact.z;
    color += half3(rim * max(-normal.y, 0.0) * mix(0.015, 0.035, targetLuminance));
    color += half3(rim * contactLight * 0.07);
    color *= half(1.0 - rim * max(normal.y, 0.0) * mix(0.07, 0.04, targetLuminance) * (0.6 + 0.4 * detail));
    return half4(clamp(color, half3(0.0), half3(1.0)) * half(mask), half(mask));
}
"""
