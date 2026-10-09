package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Known tab background drives contrast; no GPU-to-CPU color sampling or theme flipping. */
@Immutable
internal data class TabGlassMaterial(
    val accentTint: Float,
    val rimLight: Float,
    val innerShadow: Float,
    val edgeWidthDp: Float,
    val refractionDp: Float,
    val surfaceOpacity: Float,
    val outlineOpacity: Float,
)

internal fun tabGlassMaterial(background: Color, heightDp: Float): TabGlassMaterial {
    val luminance = background.luminance().coerceIn(0f, 1f)
    val thickness = ((heightDp - 48f) / 36f).coerceIn(0f, 1f)
    val darkBackground = luminance < .35f
    val darkness = (1f - luminance / .35f).coerceIn(0f, 1f)
    return TabGlassMaterial(
        accentTint = if (darkBackground) .006f + thickness * .003f else .009f + thickness * .004f,
        rimLight = if (darkBackground) .34f + thickness * .04f else .50f + thickness * .035f,
        innerShadow = if (darkBackground) .12f + thickness * .02f else .055f + thickness * .015f,
        edgeWidthDp = 1.0f + thickness * .45f,
        refractionDp = 3.3f + thickness * 1.0f,
        surfaceOpacity = if (darkBackground) .53f + thickness * .02f else .47f + thickness * .025f,
        outlineOpacity = if (darkBackground) .085f + thickness * .015f else .075f + thickness * .015f,
    )
}

/** Keep the center clear; the optical bevel, not a dense gray disk, defines small actions. */
internal fun resultActionGlassMaterial(background: Color): TabGlassMaterial {
    val dark = background.luminance() < .35f
    return tabGlassMaterial(background, 48f).copy(
        accentTint = .006f,
        surfaceOpacity = if (dark) .55f else .48f,
        rimLight = if (dark) .40f else .58f,
        innerShadow = if (dark) .07f else .04f,
        outlineOpacity = if (dark) .10f else .09f,
    )
}

/** Keep the surface color faithful to its backdrop; only a very slight theme tint is allowed. */
internal fun tabGlassFill(background: Color, accent: Color, material: TabGlassMaterial): Color {
    val tint = accent.convert(background.colorSpace)
    val emphasis = material.accentTint.coerceIn(0f, 1f)
    fun channel(base: Float, tintChannel: Float): Float = base + (tintChannel - base) * emphasis
    return Color(channel(background.red, tint.red),
        channel(background.green, tint.green),
        channel(background.blue, tint.blue), 1f, background.colorSpace)
}

/** Optical bevel uses physical pixels; 1dp would become a thick ring on dense displays. */
internal fun tabGlassEdgeWidthPx(material: TabGlassMaterial, density: Float): Float =
    (material.edgeWidthDp * .55f * density).coerceIn(1f, 1.5f)

internal fun tabGlassOutlineColor(background: Color, material: TabGlassMaterial): Color =
    (if (background.luminance() < .35f) Color.White else Color.Black)
        .copy(alpha = material.outlineOpacity)
