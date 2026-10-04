package com.luckyalanzhou.barcodegenerator.ui.app

/** Input is a GPU replay of the raw page in local coordinates, with no foreground controls. */
internal const val GLASS_BACKDROP_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float4 bounds;
uniform float4 shape;
uniform float4 contact;
uniform float capsuleMode;
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
    float lens = 1.0 - smoothstep(0.0, max(radius * 0.5, 1.0), depth);
    float2 samplePoint = clamp(p - normal * shape.z * lens, float2(0.5), resolution - 0.5);
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
    float interiorOpacity = mix(0.92, 0.94, smoothstep(0.15, 0.75, targetLuminance));
    opacity = max(opacity, mix(shape.w, interiorOpacity, interior));
    half3 color = mix(scene, surfaceColor.rgb, half(opacity));
    // Tab rim is drawn once by TabGlassSurface; only menu backgrounds own their rim here.
    float rim = (1.0 - smoothstep(0.0, 1.5, depth)) * (1.0 - capsuleMode);
    float2 lightVector = contact.xy - bounds.xy;
    float2 direction = lightVector / max(length(lightVector), 0.001);
    float contactLight = pow(max(dot(normal, direction), 0.0), mix(14.0, 4.0, clamp(contact.w, 0.0, 1.0))) * contact.z;
    color += half3(rim * max(-normal.y, 0.0) * mix(0.015, 0.035, targetLuminance));
    color += half3(rim * contactLight * 0.07);
    color *= half(1.0 - rim * max(normal.y, 0.0) * mix(0.07, 0.04, targetLuminance) * (0.6 + 0.4 * detail));
    return half4(clamp(color, half3(0.0), half3(1.0)) * half(mask), half(mask));
}
"""
