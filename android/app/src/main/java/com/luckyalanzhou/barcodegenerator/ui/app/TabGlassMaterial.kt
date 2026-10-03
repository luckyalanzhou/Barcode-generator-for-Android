package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Known tab background drives contrast; no GPU-to-CPU color sampling or theme flipping. */
@Immutable
internal data class TabGlassMaterial(
    val whiteLift: Float,
    val accentTint: Float,
    val rimLight: Float,
    val innerShadow: Float,
    val edgeWidthDp: Float,
    val refractionDp: Float,
    val surfaceOpacity: Float,
)

internal fun tabGlassMaterial(background: Color, heightDp: Float): TabGlassMaterial {
    val luminance = background.luminance().coerceIn(0f, 1f)
    val thickness = ((heightDp - 48f) / 36f).coerceIn(0f, 1f)
    val darkBackground = luminance < .35f
    val darkness = (1f - luminance / .35f).coerceIn(0f, 1f)
    return TabGlassMaterial(
        whiteLift = if (darkBackground) .022f + darkness * .014f + thickness * .01f else .12f + thickness * .05f,
        accentTint = if (darkBackground) .018f + thickness * .006f else .020f + thickness * .012f,
        rimLight = if (darkBackground) .12f + thickness * .055f else .18f + thickness * .045f,
        innerShadow = if (darkBackground) .075f + thickness * .025f else .05f + thickness * .02f,
        edgeWidthDp = .9f + thickness * .45f,
        refractionDp = 1.6f + thickness * 1.0f,
        surfaceOpacity = if (darkBackground) .42f + thickness * .06f else .30f + thickness * .08f,
    )
}
