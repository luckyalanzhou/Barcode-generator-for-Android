package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

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
