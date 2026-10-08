package com.luckyalanzhou.barcodegenerator.ui.app

/** 背景模糊与展开使用同一进度，不改变背景本身的主题颜色。 */
internal fun menuBackdropBlurPx(progress: Float, density: Float, opaqueGlass: Boolean): Float {
    if (opaqueGlass || !progress.isFinite() || !density.isFinite() || density <= 0f) return 0f
    return 20f * density * progress.coerceIn(0f, 1f)
}

/** 只用极弱黑色分离焦点层；深色模式不加白色，避免关闭时出现灰色闪屏。 */
internal fun menuBackdropDimAlpha(progress: Float, dark: Boolean): Float =
    if (!progress.isFinite()) 0f else (if (dark) .025f else .045f) * progress.coerceIn(0f, 1f)
