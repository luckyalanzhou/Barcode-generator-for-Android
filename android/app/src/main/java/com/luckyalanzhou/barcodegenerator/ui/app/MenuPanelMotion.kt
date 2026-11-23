package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin

internal fun tabMenuDragScale(offset: Offset, touchSlop: Float, resistance: Float): Float {
    if (offset.y < -touchSlop) return 1f
    val distance = (Offset(offset.x, offset.y.coerceAtLeast(0f)).getDistance() - touchSlop)
        .coerceAtLeast(0f)
    return 1f - .24f * distance / (distance + resistance)
}

/** Shrink toward the corner while leaving room for the corner itself to move inward. */
internal fun tabMenuDragOrigin(leftSide: Boolean): TransformOrigin =
    TransformOrigin(if (leftSide) .18f else .82f, .82f)

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
