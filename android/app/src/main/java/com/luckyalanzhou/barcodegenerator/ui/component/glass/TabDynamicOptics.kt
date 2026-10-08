package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** Physical-pixel dispersion only; never recolor or replay the foreground glyphs. */
internal data class TabDynamicOptics(val dispersionPx: Float, val edgeColorStrength: Float)

internal fun tabDynamicOptics(frame: TabGlassFrame): TabDynamicOptics {
    val strength = if (frame.travelStrength.isFinite()) frame.travelStrength.coerceIn(0f, 1f) else 0f
    return TabDynamicOptics(.75f * strength, .055f * strength)
}

/** Small touch-driven strain, shared by surface, backdrop mask and foreground lens. */
internal fun tabContactStrain(touchX: Float?, centerX: Float, cellWidth: Float, motion: Float): Float {
    if (touchX == null || !touchX.isFinite() || !cellWidth.isFinite() || cellWidth <= 0f) return 0f
    return (kotlin.math.abs(touchX - centerX) / (cellWidth * .5f)).coerceIn(0f, 1f) *
        motion.coerceIn(0f, 1f) * .02f
}
