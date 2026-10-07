package com.luckyalanzhou.barcodegenerator.ui.app

/** Shared optical presets. New glass controls reuse the material/shaders and a role preset,
 * rather than introducing a per-page shader, tint curve or unbounded refraction strength.
 * Geometry/gesture ownership remains with the actual control, not the material renderer.
 */
internal object GlassControlDefaults {
    const val TabBlurDp = .5f
    const val TabRestRefractionDp = .8f
    const val MaxBackdropRefractionDp = 4.4f
    const val MaxForegroundRefractionDp = 2.8f
    const val RoundActionBlurDp = .65f
    const val RoundActionRestRefractionDp = 1.2f
    const val RoundActionPressRefractionDp = 1.1f
}
