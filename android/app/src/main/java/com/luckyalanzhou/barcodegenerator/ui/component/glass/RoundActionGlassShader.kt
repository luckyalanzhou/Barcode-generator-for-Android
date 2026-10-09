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
    half3 localAverage = half3(0.0);
    float minimum = 1.0;
    float maximum = 0.0;
    // 近场只检测细节，不能直接作为扩散结果，否则规则采样会保留条码条纹。
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            half3 tap = (x == 0 && y == 0) ? center :
                content.eval(clamp(samplePoint + float2(float(x), float(y)) * spread,
                    float2(0.5), resolution - 0.5)).rgb;
            float brightness = dot(float3(tap), float3(0.2126, 0.7152, 0.0722));
            minimum = min(minimum, brightness);
            maximum = max(maximum, brightness);
            localAverage += tap / 9.0;
        }
    }
    // 宽场扩散开始：固定十二个非网格采样点，避免与条码周期对齐产生摩尔纹。
    // 范围按 dp 缩放并限制在按钮半径内，不增加 CPU 读回或随帧随机噪点。
    float diffusionRadius = min(max(spread * 2.8, density * 7.0), radius * 0.42);
    half3 diffused = half3(0.0);
    for (int i = 0; i < 12; i++) {
        float angle = float(i) * 2.399963;
        float distance = sqrt((float(i) + 0.5) / 12.0) * diffusionRadius;
        half3 tap = content.eval(clamp(samplePoint + float2(cos(angle), sin(angle)) * distance,
            float2(0.5), resolution - 0.5)).rgb;
        float brightness = dot(float3(tap), float3(0.2126, 0.7152, 0.0722));
        minimum = min(minimum, brightness);
        maximum = max(maximum, brightness);
        diffused += tap / 12.0;
    }
    float detail = smoothstep(0.035, 0.30, maximum - minimum);
    half3 scene = mix(center, mix(localAverage, diffused, half(0.92)), half(detail * 0.98));
    // 宽场扩散结束
    float luminance = dot(float3(scene), float3(0.2126, 0.7152, 0.0722));
    float base = dot(float3(surfaceColor.rgb), float3(0.2126, 0.7152, 0.0722));
    // 同色背景不增加灰底，跨明暗的复杂内容才逐渐提高背景保护。
    float protection = smoothstep(0.12, 0.70, abs(luminance - base)) * 0.46 + detail * 0.16;
    float opacity = clamp(shape.w + protection, 0.0, 1.0);
    half3 color = mix(scene, surfaceColor.rgb, half(opacity));
    float lightSurface = smoothstep(0.15, 0.75, base);
    float2 lightDirection = normalize(float2(-0.35, -1.0) +
        (contact.xy - bounds.xy) / max(radius, 1.0) * motion * 0.20);
    float facing = max(dot(normal, lightDirection), 0.0);
    float opposite = max(-dot(normal, lightDirection), 0.0);
    // 外缘、暗切面、内倒角使用不同深度；高密度下也保留真实间距。
    // 外缘仍限制为细像素带，只有内部光学过渡按 dp 缩放，不叠加 Canvas 外圈。
    float edge = exp(-depth / clamp(density * 0.45, 0.8, 1.4));
    float cut = exp(-pow((depth - (density * 0.65 + 0.4)) / max(density * 0.42, 0.65), 2.0));
    float bevel = exp(-pow((depth - (density * 1.65 + 0.4)) / max(density * 0.55, 0.8), 2.0));
    float shade = cut * mix(0.035, 0.10, lightSurface) +
        bevel * opposite * mix(0.035, 0.045, lightSurface);
    color *= half(1.0 - shade * (1.0 - shape.w));
    // 顶部镜面亮线与更靠内的柔和反射连接，侧边保留弱反射，不全周画白圈。
    float reflection = edge * (pow(facing, 3.0) * mix(0.38, 0.70, lightSurface) +
        (1.0 - facing) * mix(0.07, 0.025, lightSurface)) +
        bevel * (pow(facing, 2.0) * mix(0.18, 0.38, lightSurface) +
        (1.0 - facing) * mix(0.035, 0.04, lightSurface));
    reflection *= (1.0 + motion * 0.12) * (1.0 - shape.w);
    color = mix(color, half3(1.0), half(clamp(reflection, 0.0, 0.75)));
    // 静态切面增益开始：静止时形成可读的内侧反射面，不用加深外圈补质感。
    // 手势开始后平滑退出；原有按压光学、折射位移与外部投影保持原路径。
    float stationary = 1.0 - smoothstep(0.0, 0.40, motion);
    float innerFacet = exp(-pow((depth - density * 3.0) / max(density * 0.85, 0.9), 2.0));
    float topFacet = pow(facing, 2.0) * mix(0.12, 0.78, lightSurface);
    float counterFacet = pow(opposite, 3.0) * mix(0.045, 0.09, lightSurface);
    float facetReflection = innerFacet * (topFacet + counterFacet) * stationary * (1.0 - shape.w);
    color = mix(color, half3(1.0), half(facetReflection));
    // 暗切面与反射面之间保留过渡，侧面有层次但不铺满整个内部。
    color *= half(1.0 - cut * facing * mix(0.015, 0.035, lightSurface) * stationary * (1.0 - shape.w));
    // 静态切面增益结束
    return half4(clamp(color, half3(0.0), half3(1.0)) * half(mask), half(mask));
}
"""
