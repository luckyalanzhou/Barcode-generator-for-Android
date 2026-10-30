package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** 共用圆角透镜轮廓：边界和中心为零，峰值位于倒角内部。
 * 背景与移动前景使用同一形状，不分别调整曲线。
 * 这是有界近似效果，并非 Apple 专有材质的实现。
 */
internal const val GLASS_LENS_PROFILE_SHADER = """
float glassLensProfile(float depth, float radius, float density) {
    float band = max(radius * 0.85, max(density, 0.1) * 3.0);
    float t = clamp(depth / band, 0.0, 1.0);
// 两端使用平滑导数，避免移动时出现硬切边或重复轮廓。
    float bell = 4.0 * t * (1.0 - t);
    return bell * bell;
}
"""
