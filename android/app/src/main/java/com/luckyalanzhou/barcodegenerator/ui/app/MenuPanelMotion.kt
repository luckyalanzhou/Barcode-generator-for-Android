package com.luckyalanzhou.barcodegenerator.ui.app

// HIG：上下文菜单标题只有在能说明操作对象或操作结果时才有价值。
internal fun menuShowsTitle(title: String): Boolean = title.isNotBlank()

/** 使用同一个展开进度驱动几何、光线和厚度，不让各部分独立淡入淡出。 */
internal data class MenuGlassReveal(val scale: Float, val alpha: Float, val thickness: Float, val shadow: Float)

internal fun menuGlassReveal(progress: Float, reduceMotion: Boolean): MenuGlassReveal {
    val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    if (reduceMotion) return MenuGlassReveal(1f, p, 1f, 1f)
    // 两类菜单都整体展开；仅允许很小的弹簧超调，不通过裁切隐藏菜单行。
    val elastic = if (progress.isFinite()) progress.coerceIn(0f, 1.08f) else 0f
    return MenuGlassReveal(.94f + .06f * elastic,
        p, p * p * (3f - 2f * p), .35f + .65f * p)
}
