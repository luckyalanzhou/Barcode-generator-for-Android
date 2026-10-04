package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

internal data class TabMenuPlacement(val left: Float, val top: Float, val pivotX: Float, val anchorCenterX: Float)

/** Physical pixel geometry shared by measurement and regression tests. Lift is applied to both foregrounds. */
internal fun tabMenuPlacement(
    anchor: Rect, origin: Offset, panel: IntSize, screenWidth: Float,
    statusTop: Float, edgePadding: Float, gap: Float, lift: Float,
    above: Boolean = true,
): TabMenuPlacement {
    val center = anchor.center.x - origin.x
    val left = (center - panel.width / 2f).coerceIn(edgePadding,
        (screenWidth - panel.width - edgePadding).coerceAtLeast(edgePadding))
    val top = if (above) (anchor.top - origin.y - panel.height - gap).coerceAtLeast(statusTop + gap + lift)
        else anchor.bottom - origin.y + gap + lift
    val pivot = if (panel.width > 0) ((center - left) / panel.width).coerceIn(0f, 1f) else .5f
    return TabMenuPlacement(left, top, pivot, center)
}

internal data class ContextMenuSpace(val above: Boolean, val height: Float)

/** Bottom Tabs remain above their anchor; rows choose the side with enough usable space. */
internal fun contextMenuSpace(
    anchor: Rect, origin: Offset, screenHeight: Float, statusTop: Float, bottomInset: Float,
    gap: Float, lift: Float, desiredHeight: Float, tabAnchor: Boolean,
): ContextMenuSpace {
    val above = (anchor.top - origin.y - statusTop - gap * 2 - lift).coerceAtLeast(0f)
    val below = (screenHeight - bottomInset - gap * 2 - anchor.bottom + origin.y - lift).coerceAtLeast(0f)
    val useAbove = tabAnchor || above >= desiredHeight || above >= below
    return ContextMenuSpace(useAbove, if (useAbove) above else below)
}
