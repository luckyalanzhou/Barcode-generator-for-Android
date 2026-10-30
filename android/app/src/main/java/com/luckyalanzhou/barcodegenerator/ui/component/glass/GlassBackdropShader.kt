package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** 输入是在 GPU 上按局部坐标重放的原始页面，不包含前景控件。 */
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
// 柔和的触点波纹会弯曲胶囊内部及边缘处的实时背景。
// 效果跟随实际触点，且不会绘制到遮罩范围之外。
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
// 对环境进行空间平滑，也适用于低模糊 Tab；不回读 CPU 像素，也不切换主题。
    float2 spread = float2(max(shape.y * 0.35, 2.0), 0.0);
    scene += content.eval(clamp(samplePoint + spread, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(samplePoint - spread, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(samplePoint + spread.yx, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(samplePoint - spread.yx, float2(0.5), resolution - 0.5)).rgb * 0.15;
// 通过少量局部 GPU 采样保护对比度，不回读 CPU 像素，也不改变全局主题。
    float luminance = dot(float3(scene), float3(0.2126, 0.7152, 0.0722));
    float targetLuminance = dot(float3(surfaceColor.rgb), float3(0.2126, 0.7152, 0.0722));
    float detail = smoothstep(0.015, 0.35, length(float3(center - scene)));
    float protection = smoothstep(0.12, 0.75, abs(luminance - targetLuminance)) * 0.24 + detail * 0.10;
    // 菜单已有较厚底色与模糊：额外保护减半，避免深色面板遇到亮背景直接变成实色。
    protection *= mix(1.0, 0.5, menuMaterial);
    float opacity = clamp(shape.w + protection, 0.0, 1.0);
// 菜单包含文字标签：降低内部背景干扰，但不把边缘变成不透明色块。
// 胶囊继续使用原有材质，此处理不能给整个 Tab 栏染色。
    float interior = smoothstep(1.5, 12.0, depth) * (1.0 - capsuleMode);
    // 新菜单采用自己的厚度参数；原有 Tab/结果页控件保持原路径，避免材质迁移影响其他控件。
    float interiorOpacity = mix(mix(0.92, 0.94, smoothstep(0.15, 0.75, targetLuminance)), shape.w, menuMaterial);
    opacity = max(opacity, mix(shape.w, interiorOpacity, interior));
    half3 materialTint = capsuleMode > 0.5 ? glassAdaptiveTint(surfaceColor.rgb, scene, opacity) : surfaceColor.rgb;
    half3 color = mix(scene, materialTint, half(opacity));
    // 静态边缘由 TabGlassSurface 统一绘制；静止时着色器不再叠加第二层描边。
// 文字或细节经过小控件时，只增强其内侧边缘的分离度。
    float separation = capsuleMode * (1.0 - smoothstep(1.0, 5.0, depth)) * detail * 0.045;
    color *= half(1.0 - separation);
// 仅胶囊在移动时启用边缘光学效果；不采样或重着色前景图集。
// 额外的两次采样仅用于内侧边缘带；不保存帧历史，也不回读 CPU 像素。
    float capsuleEdge = capsuleMode * (1.0 - smoothstep(0.0, 2.0, depth)) *
        smoothstep(0.0, 0.65, depth);
    if (capsuleEdge > 0.001 && capsuleOptics.x > 0.001) {
        float2 split = normal * capsuleOptics.x;
        half3 redSide = content.eval(clamp(samplePoint + split, float2(0.5), resolution - 0.5)).rgb;
        half3 blueSide = content.eval(clamp(samplePoint - split, float2(0.5), resolution - 0.5)).rgb;
        half3 spectrum = half3(redSide.r, center.g, blueSide.b);
        color += (spectrum - center) * half(capsuleEdge * (1.0 - opacity) * 0.65);
// 使用有界的局部环境色，不采用 Add/Color Dodge 混合，也不绘制外部霓虹光晕。
        float darkSurface = 1.0 - smoothstep(0.05, 0.35, targetLuminance);
        color += clamp(scene - half3(luminance), half3(-0.35), half3(0.35)) *
            half(capsuleEdge * capsuleOptics.y * darkSurface);
    }
// Tab 边缘由 TabGlassSurface 绘制一次；此处仅为菜单背景绘制边缘。
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
