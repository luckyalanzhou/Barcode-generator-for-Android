package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** 背景和前景的对比度估算共用此逻辑；不回读像素，也不修改主题。 */
internal const val GLASS_ADAPTIVE_TINT_SHADER = """
half3 glassAdaptiveTint(half3 base, half3 scene, float opacity) {
// 根据实际背景调整，不注入固定的灰色或白色主体色调。
// 菜单和不透明的无障碍表面仍使用各自的对比度处理方式。
    float adaptation = 0.42 * (1.0 - 0.20 * clamp(opacity, 0.0, 1.0));
    return mix(base, clamp(scene, half3(0.0), half3(1.0)), half(adaptation));
}
"""
