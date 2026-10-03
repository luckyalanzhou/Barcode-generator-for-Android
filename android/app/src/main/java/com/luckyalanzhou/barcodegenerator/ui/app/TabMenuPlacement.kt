package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

internal data class TabMenuPlacement(val left: Float, val top: Float, val pivotX: Float, val anchorCenterX: Float)

/** Physical pixel geometry shared by measurement and regression tests. Lift is applied to both foregrounds. */
internal fun tabMenuPlacement(
    anchor: Rect, origin: Offset, panel: IntSize, screenWidth: Float,
    statusTop: Float, edgePadding: Float, gap: Float, lift: Float,
): TabMenuPlacement {
    val center = anchor.center.x - origin.x
    val left = (center - panel.width / 2f).coerceIn(edgePadding,
        (screenWidth - panel.width - edgePadding).coerceAtLeast(edgePadding))
    val top = (anchor.top - origin.y - panel.height - gap).coerceAtLeast(statusTop + gap + lift)
    val pivot = if (panel.width > 0) ((center - left) / panel.width).coerceIn(0f, 1f) else .5f
    return TabMenuPlacement(left, top, pivot, center)
}
