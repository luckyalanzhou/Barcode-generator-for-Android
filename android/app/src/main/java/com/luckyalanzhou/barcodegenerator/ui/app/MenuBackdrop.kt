package com.luckyalanzhou.barcodegenerator.ui.app

/** 长按菜单的背景聚焦独立于菜单材质，与展开使用同一进度。 */
internal fun menuBackdropBlurPx(progress: Float, density: Float, reduceMotion: Boolean): Float {
    if (reduceMotion || !progress.isFinite() || !density.isFinite() || density <= 0f) return 0f
    return 20f * density * progress.coerceIn(0f, 1f)
}

/** 轻微压低背景对比度，同时保留整页模糊内容。 */
internal fun menuBackdropDimAlpha(progress: Float, dark: Boolean): Float =
    if (!progress.isFinite()) 0f else (if (dark) .025f else .045f) * progress.coerceIn(0f, 1f)
