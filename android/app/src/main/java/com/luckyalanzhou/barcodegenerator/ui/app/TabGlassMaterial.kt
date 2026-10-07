package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Known tab background drives contrast; no GPU-to-CPU color sampling or theme flipping. */
@Immutable
internal data class TabGlassMaterial(
    val bodyTintStrength: Float,
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
        bodyTintStrength = if (darkBackground) .085f + darkness * .015f + thickness * .005f else .14f + thickness * .02f,
        accentTint = if (darkBackground) .006f + thickness * .003f else .008f + thickness * .004f,
        rimLight = if (darkBackground) .20f + thickness * .025f else .32f + thickness * .035f,
        innerShadow = if (darkBackground) .085f + thickness * .015f else .035f + thickness * .01f,
        edgeWidthDp = .9f + thickness * .45f,
        refractionDp = 3.3f + thickness * 1.0f,
        surfaceOpacity = if (darkBackground) .58f + thickness * .06f else .54f + thickness * .06f,
        outlineOpacity = if (darkBackground) .045f + thickness * .01f else .035f + thickness * .01f,
    )
}

/** Small isolated actions need more body separation than the wider selected Tab. */
internal fun resultActionGlassMaterial(background: Color): TabGlassMaterial {
    val dark = background.luminance() < .35f
    return tabGlassMaterial(background, 48f).copy(
        bodyTintStrength = if (dark) .12f else .22f,
        accentTint = .006f,
        surfaceOpacity = if (dark) .60f else .72f,
        rimLight = if (dark) .30f else .72f,
        innerShadow = if (dark) .24f else .10f,
        outlineOpacity = if (dark) .075f else .055f,
    )
}

/** Same baseline on GPU and fallback: a visible neutral body, not a blue glow or white highlight. */
internal fun tabGlassFill(background: Color, accent: Color, material: TabGlassMaterial): Color {
    val neutral = (if (background.luminance() < .35f) Color.White else Color(0xFF9098A2)).convert(background.colorSpace)
    val tint = accent.convert(background.colorSpace)
    val body = material.bodyTintStrength.coerceIn(0f, 1f)
    val emphasis = material.accentTint.coerceIn(0f, 1f)
    fun channel(base: Float, neutralChannel: Float, tintChannel: Float): Float {
        val value = base + (neutralChannel - base) * body
        return value + (tintChannel - value) * emphasis
    }
    return Color(channel(background.red, neutral.red, tint.red),
        channel(background.green, neutral.green, tint.green),
        channel(background.blue, neutral.blue, tint.blue), 1f, background.colorSpace)
}

/** Optical bevel uses physical pixels; 1dp would become a thick ring on dense displays. */
internal fun tabGlassEdgeWidthPx(material: TabGlassMaterial, density: Float): Float =
    (material.edgeWidthDp * .55f * density).coerceIn(1f, 1.5f)

internal fun tabGlassOutlineColor(background: Color, material: TabGlassMaterial): Color =
    (if (background.luminance() < .35f) Color.White else Color.Black)
        .copy(alpha = material.outlineOpacity)
