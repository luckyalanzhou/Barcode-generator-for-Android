package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import androidx.compose.ui.graphics.TransformOrigin

/** Lower drag gain slows the source without queuing animations for every pointer update. */
internal object TabMenuSourceMotion {
    const val dragRangeDp = 96f
    const val horizontalLimitDp = 20f
    const val verticalLimitDp = 22f
    const val returnStiffness = 300f
    const val returnDamping = .95f
}

internal fun tabMenuIconMotion(motion: Offset): Offset {
    val ratio = TabMenuSourceMotion.dragRangeDp / 64f
    fun resist(value: Float): Float = value / (ratio + (1f - ratio) * abs(value))
    return Offset(resist(motion.x), resist(motion.y))
}

/** Tab menus stay in place and contract toward their own lower corner. */
internal fun tabMenuDragOrigin(anchorCenterX: Float, screenWidth: Float): TransformOrigin =
    TransformOrigin(if (anchorCenterX < screenWidth * .5f) 0f else 1f, 1f)

// HIG: a context-menu title is useful only when it explains the target or effect.
internal fun menuShowsTitle(tabAnchor: Boolean, title: String): Boolean =
    title.isNotBlank() && (!tabAnchor || title != "操作")

/** A single reveal coordinate for geometry, light and thickness; no independent fades. */
internal data class MenuGlassReveal(val scale: Float, val alpha: Float, val thickness: Float, val shadow: Float)

internal fun menuGlassReveal(progress: Float, tabAnchor: Boolean, reduceMotion: Boolean): MenuGlassReveal {
    val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    if (reduceMotion) return MenuGlassReveal(1f, if (p > 0f) 1f else 0f, 1f, 1f)
    val initialScale = if (tabAnchor) .94f else .86f
    return MenuGlassReveal(initialScale + (1f - initialScale) * p,
        (p / .2f).coerceIn(0f, 1f), p * p * (3f - 2f * p), .35f + .65f * p)
}

/** Normalized, bounded motion: the panel moves as one surface, not individual glyphs. */
internal fun menuPanelMotion(point: Offset?, panel: Rect): Offset {
    if (point == null || panel.width <= 0f || panel.height <= 0f) return Offset.Zero
    return Offset(
        ((point.x - panel.center.x) / (panel.width / 2f)).coerceIn(-1f, 1f),
        ((point.y - panel.center.y) / (panel.height / 2f)).coerceIn(-1f, 1f),
    )
}

/** The finger stays on the source icon: displacement starts at the long-press origin. */
internal fun menuAnchorMotion(point: Offset?, origin: Offset, rangePx: Float): Offset {
    if (point == null || rangePx <= 0f) return Offset.Zero
    val delta = point - origin
    // Rubber resistance keeps responding to larger drags instead of hitting a hard stop.
    return Offset(delta.x / (rangePx + abs(delta.x)),
        delta.y / (rangePx + abs(delta.y)))
}

internal fun menuDragScale(motion: Offset): Float =
    1f - .20f * maxOf(abs(motion.x), abs(motion.y)).coerceIn(0f, 1f)

/** Sliding toward the menu selects rows, rather than pushing the menu away. */
internal fun menuSourceInteractionMotion(
    point: Offset?, origin: Offset, rangePx: Float, menuAbove: Boolean,
): Offset {
    if (point == null) return Offset.Zero
    val delta = point - origin
    val towardMenu = if (menuAbove) delta.y < 0f else delta.y > 0f
    if (towardMenu && abs(delta.y) >= abs(delta.x)) return Offset.Zero
    return menuAnchorMotion(point, origin, rangePx)
}

/** Do not subscribe active dragging to the independently animated return state. */
internal inline fun menuMotionForDrawing(
    dragMotion: Offset?, reduceMotion: Boolean, returnMotion: () -> Offset,
): Offset = if (reduceMotion) Offset.Zero else dragMotion ?: returnMotion()
