package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** 固定静态光学帧，背景采样可以更新，但触点与运动不参与材质。 */
internal fun roundActionGlassFrame(
    width: Float,
    height: Float,
    density: Float,
): TabGlassFrame {
    val safeWidth = width.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val safeHeight = height.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val safeDensity = density.takeIf(Float::isFinite)?.coerceAtLeast(.1f) ?: .1f
    val centerX = safeWidth * .5f
    val centerY = safeHeight * .5f
    return TabGlassFrame(
        width = safeWidth,
        height = safeHeight,
        centerX = centerX,
        centerY = centerY,
        halfWidth = centerX,
        halfHeight = centerY,
        motion = 0f,
        refractionPx = GlassControlDefaults.RoundActionRestRefractionDp * safeDensity,
        density = safeDensity,
        touchX = centerX,
        touchY = centerY,
        contactSpread = .72f,
        travelStrength = 0f,
    )
}
