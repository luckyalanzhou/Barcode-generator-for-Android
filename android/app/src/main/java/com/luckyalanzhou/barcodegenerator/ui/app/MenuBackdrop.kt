package com.luckyalanzhou.barcodegenerator.ui.app

/** 背景模糊与展开使用同一进度，不改变背景本身的主题颜色。 */
internal fun menuBackdropBlurPx(progress: Float, density: Float, opaqueGlass: Boolean): Float {
    if (opaqueGlass || !progress.isFinite() || !density.isFinite() || density <= 0f) return 0f
    return 32f * density * progress.coerceIn(0f, 1f)
}

/** 轻微压低背景对比度，同时保留整页模糊内容。 */
internal fun menuBackdropDimAlpha(progress: Float, dark: Boolean): Float =
    if (!progress.isFinite()) 0f else (if (dark) .025f else .045f) * progress.coerceIn(0f, 1f)
