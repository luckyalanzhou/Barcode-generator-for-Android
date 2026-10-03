package com.luckyalanzhou.barcodegenerator.ui.app

/** Input is a GPU replay of the raw page in local coordinates, with no foreground controls. */
internal const val GLASS_BACKDROP_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float4 bounds;
uniform float4 shape;
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
    half3 scene = content.eval(samplePoint).rgb * 0.4;
    float2 spread = float2(shape.y, 0.0);
    scene += content.eval(clamp(samplePoint + spread, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(samplePoint - spread, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(samplePoint + spread.yx, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(samplePoint - spread.yx, float2(0.5), resolution - 0.5)).rgb * 0.15;
    half3 color = mix(scene, surfaceColor.rgb, half(clamp(shape.w, 0.0, 1.0)));
    float rim = 1.0 - smoothstep(0.0, 1.5, depth);
    color += half3(rim * max(-normal.y, 0.0) * 0.045);
    color *= half(1.0 - rim * max(normal.y, 0.0) * 0.06);
    return half4(clamp(color, half3(0.0), half3(1.0)) * half(mask), half(mask));
}
"""
