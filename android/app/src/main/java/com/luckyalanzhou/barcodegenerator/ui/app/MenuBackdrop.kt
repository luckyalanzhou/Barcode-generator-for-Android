package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.graphics.Color

/** 长按菜单的背景聚焦独立于菜单材质，与展开使用同一进度。 */
internal fun menuBackdropBlurPx(progress: Float, density: Float, reduceMotion: Boolean): Float {
    if (reduceMotion || !progress.isFinite() || !density.isFinite() || density <= 0f) return 0f
    return 20f * density * progress.coerceIn(0f, 1f)
}

/** 轻微压低背景对比度，同时保留整页模糊内容。 */
internal fun menuBackdropDimAlpha(progress: Float, dark: Boolean): Float =
    if (!progress.isFinite()) 0f else (if (dark) .025f else .045f) * progress.coerceIn(0f, 1f)

internal fun menuBackdropVeil(progress: Float, dark: Boolean, frosted: Boolean): Color {
    if (!frosted) return Color.Black.copy(alpha = menuBackdropDimAlpha(progress, dark))
    val reveal = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    return if (dark) Color.Black.copy(alpha = .12f * reveal)
    else Color.White.copy(alpha = .10f * reveal)
}

/** Let the panel pick up color only once the page is substantially blurred. */
internal fun frostedMenuPanelAlpha(progress: Float, dark: Boolean): Float {
    if (!progress.isFinite()) return 1f
    val reveal = ((progress - .75f) / .25f).coerceIn(0f, 1f)
    return 1f - (if (dark) .10f else .14f) * reveal
}
