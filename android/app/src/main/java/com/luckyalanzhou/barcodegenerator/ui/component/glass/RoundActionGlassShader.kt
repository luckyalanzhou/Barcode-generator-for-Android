package com.luckyalanzhou.barcodegenerator.ui.component.glass

/**
 * 圆形工具按钮独立材质：复杂背景扩散、暗边、镜面高光分别计算。
 * 参数是 Android 待真机校准的近似值，不代表苹果公开的光学公式。
 * 输入只有页面背景，图标随后清晰绘制；不影响 Tab 和菜单的既有路径。
 */
internal const val ROUND_ACTION_GLASS_SHADER = """
half4 roundActionMaterial(float2 p, float2 normal, float depth, float mask) {
    float density = max(pixelDensity, 0.1);
    float motion = clamp(contact.z, 0.0, 1.0);
    float radius = min(bounds.z, bounds.w);
    float lens = glassLensProfile(depth, radius, density);
    // 只在边缘弯曲真实背景，不把整块按钮当放大镜。
    float2 samplePoint = clamp(p - normal * shape.z * lens, float2(0.5), resolution - 0.5);
    half3 center = content.eval(samplePoint).rgb;
    float spread = max(shape.y, density);
    half3 wide = half3(0.0);
    float minimum = 1.0;
    float maximum = 0.0;
    // 固定九次采样上限，不读取 CPU 位图；复杂程度由周围亮度差决定。
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            half3 tap = (x == 0 && y == 0) ? center :
                content.eval(clamp(samplePoint + float2(float(x), float(y)) * spread,
                    float2(0.5), resolution - 0.5)).rgb;
            float brightness = dot(float3(tap), float3(0.2126, 0.7152, 0.0722));
            minimum = min(minimum, brightness);
            maximum = max(maximum, brightness);
            wide += tap / 9.0;
        }
    }
    float detail = smoothstep(0.035, 0.30, maximum - minimum);
    half3 scene = mix(center, wide, half(detail * 0.82));
    float luminance = dot(float3(scene), float3(0.2126, 0.7152, 0.0722));
    float base = dot(float3(surfaceColor.rgb), float3(0.2126, 0.7152, 0.0722));
    // 同色背景不增加灰底，跨明暗的复杂内容才逐渐提高背景保护。
    float protection = smoothstep(0.12, 0.70, abs(luminance - base)) * 0.46 + detail * 0.08;
    float opacity = clamp(shape.w + protection, 0.0, 1.0);
    half3 color = mix(scene, surfaceColor.rgb, half(opacity));
    float lightSurface = smoothstep(0.15, 0.75, base);
    float2 lightDirection = normalize(float2(-0.35, -1.0) +
        (contact.xy - bounds.xy) / max(radius, 1.0) * motion * 0.20);
    float facing = max(dot(normal, lightDirection), 0.0);
    float opposite = max(-dot(normal, lightDirection), 0.0);
    // 物理像素细边与内倒角分离；不叠加额外 Canvas 外圈。
    float edge = exp(-depth / 0.75);
    float bevel = exp(-pow((depth - density * 0.75) / max(density * 0.65, 0.8), 2.0));
    float shade = edge * mix(0.06, 0.11, lightSurface) * (1.0 - facing * 0.65) +
        bevel * opposite * mix(0.06, 0.075, lightSurface);
    color *= half(1.0 - shade * (1.0 - shape.w));
    float reflection = edge * pow(facing, 3.0) * mix(0.42, 0.78, lightSurface) +
        bevel * facing * mix(0.075, 0.14, lightSurface) + edge * opposite * 0.035;
    reflection *= (1.0 + motion * 0.12) * (1.0 - shape.w);
    color = mix(color, half3(1.0), half(clamp(reflection, 0.0, 0.75)));
    // 高光在浅色背景上会抵消暗边：在高光内侧保留连续的细切面。
    // 宽度使用物理像素，不随屏幕密度变成厚圈；中心和外部阴影不参与。
    float cut = exp(-pow((depth - 1.45) / 0.65, 2.0)) * (1.0 - shape.w);
    color *= half(1.0 - cut * mix(0.025, 0.065, lightSurface));
    // 暗背景用少量反射维持同一条切面，不把整块按钮提亮成灰片。
    color = mix(color, half3(1.0), half(cut * (1.0 - lightSurface) * 0.055));
    return half4(clamp(color, half3(0.0), half3(1.0)) * half(mask), half(mask));
}
"""
