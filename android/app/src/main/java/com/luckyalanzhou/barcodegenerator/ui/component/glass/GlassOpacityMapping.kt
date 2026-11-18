package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** Maps a persisted glass strength percentage onto a caller's existing opacity baseline. */
internal fun glassOpacityForStrength(baseOpacity: Float, strengthPercent: Int): Float {
    val baseline = if (baseOpacity.isFinite()) baseOpacity.coerceIn(0f, 1f) else 0.5f
    if (baseline == 1f) return 1f

    val strength = strengthPercent.coerceIn(0, 100) / 100f
    val opacity = if (strength <= 0.5f) {
        val progress = strength / 0.5f
        baseline * (0.75f + 0.25f * progress)
    } else {
        val progress = (strength - 0.5f) / 0.5f
        baseline + (1f - baseline) * 0.35f * progress
    }
    return opacity.coerceIn(0f, 1f)
}
