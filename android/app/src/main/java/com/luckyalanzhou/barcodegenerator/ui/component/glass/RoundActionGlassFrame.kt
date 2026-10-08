package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.geometry.Offset

internal fun roundActionGlassFrame(
    width: Float,
    height: Float,
    density: Float,
    activity: Float,
    touch: Offset?,
): TabGlassFrame {
    val safeWidth = width.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val safeHeight = height.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val safeDensity = density.takeIf(Float::isFinite)?.coerceAtLeast(.1f) ?: .1f
    val centerX = safeWidth * .5f
    val centerY = safeHeight * .5f
    val strength = activity.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return TabGlassFrame(
        width = safeWidth,
        height = safeHeight,
        centerX = centerX,
        centerY = centerY,
        halfWidth = centerX,
        halfHeight = centerY,
        motion = strength,
        refractionPx = (GlassControlDefaults.RoundActionRestRefractionDp +
            GlassControlDefaults.RoundActionPressRefractionDp * strength) * safeDensity,
        density = safeDensity,
        touchX = touch?.x?.takeIf(Float::isFinite)?.coerceIn(0f, safeWidth) ?: centerX,
        touchY = touch?.y?.takeIf(Float::isFinite)?.coerceIn(0f, safeHeight) ?: centerY,
        contactSpread = .72f,
        travelStrength = strength,
    )
}
