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
)

internal fun tabGlassMaterial(background: Color, heightDp: Float): TabGlassMaterial {
    val luminance = background.luminance().coerceIn(0f, 1f)
    val thickness = ((heightDp - 48f) / 36f).coerceIn(0f, 1f)
    val darkBackground = luminance < .35f
    val darkness = (1f - luminance / .35f).coerceIn(0f, 1f)
    return TabGlassMaterial(
        bodyTintStrength = if (darkBackground) .11f + darkness * .02f + thickness * .01f else .14f + thickness * .025f,
        accentTint = if (darkBackground) .006f + thickness * .003f else .008f + thickness * .004f,
        rimLight = if (darkBackground) .12f + thickness * .055f else .18f + thickness * .045f,
        innerShadow = if (darkBackground) .075f + thickness * .025f else .05f + thickness * .02f,
        edgeWidthDp = .9f + thickness * .45f,
        refractionDp = 1.6f + thickness * 1.0f,
        surfaceOpacity = if (darkBackground) .58f + thickness * .06f else .54f + thickness * .06f,
    )
}

/** Same baseline on GPU and fallback: a visible neutral body, not a blue glow or white highlight. */
internal fun tabGlassFill(background: Color, accent: Color, material: TabGlassMaterial): Color {
    val neutral = (if (background.luminance() < .35f) Color.White else Color(0xFF64748B)).convert(background.colorSpace)
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
