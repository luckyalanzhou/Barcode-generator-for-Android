package com.luckyalanzhou.barcodegenerator.ui.app

/** The backdrop only changes spatial blur; its theme color never gets an animated veil. */
internal fun menuBackdropBlurPx(progress: Float, density: Float, opaqueGlass: Boolean): Float {
    if (opaqueGlass || !progress.isFinite() || !density.isFinite() || density <= 0f) return 0f
    return 20f * density * progress.coerceIn(0f, 1f)
}
