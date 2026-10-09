package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** Shared optical presets. New glass controls reuse the material/shaders and a role preset,
 * rather than introducing a per-page shader, tint curve or unbounded refraction strength.
 * Geometry/gesture ownership remains with the actual control, not the material renderer.
 */
internal object GlassControlDefaults {
    const val TabBlurDp = .5f
    const val TabRestRefractionDp = .8f
    // Resting glass remains quiet; motion gets a bounded optical lift instead of
    // turning the whole selected tab into a moving magnifier.
    const val MaxBackdropRefractionDp = 3.0f
    const val MaxForegroundRefractionDp = 1.6f
    // 扩散采样半径，不是整块按钮的高斯模糊；仅复杂背景启用。
    const val RoundActionBlurDp = 2.5f
    const val RoundActionRestRefractionDp = .55f
}
