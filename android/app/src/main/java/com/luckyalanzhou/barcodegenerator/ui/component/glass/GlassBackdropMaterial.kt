package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** 提供有界的基础效果；GPU 只在局部增强对比度，不切换应用主题。 */
@Immutable
internal data class GlassBackdropMaterial(val opacity: Float, val blurDp: Float, val refractionDp: Float)

internal fun menuGlassMaterial(background: Color, heightDp: Float): GlassBackdropMaterial {
    val dark = background.luminance() < .35f
    val thickness = ((heightDp - 80f) / 160f).coerceIn(0f, 1f)
    return GlassBackdropMaterial(
        opacity = (if (dark) .64f else .56f) + .05f * thickness,
        blurDp = 7f + thickness * 3f,
        refractionDp = .5f + thickness * .35f,
    )
}
