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
    // 以实际来源为中心，只有空间不足时向安全区域偏移。
    val desiredLeft = center - panel.width / 2f
    val left = desiredLeft.coerceIn(edgePadding,
        (screenWidth - panel.width - edgePadding).coerceAtLeast(edgePadding))
    val top = if (above) (anchor.top - origin.y - panel.height - gap).coerceAtLeast(statusTop + gap + lift)
        else anchor.bottom - origin.y + gap + lift
    val pivot = if (panel.width > 0) ((center - left) / panel.width).coerceIn(0f, 1f) else .5f
    return TabMenuPlacement(left, top, pivot, center)
}

internal data class ContextMenuSpace(val above: Boolean, val height: Float)

/** Keep a row and its menu together; make room below by lifting the clear source card. */
internal fun liftedRowMenuAnchor(
    row: Rect, origin: Offset, screenHeight: Float, statusTop: Float,
    bottomInset: Float, gap: Float, desiredHeight: Float,
): Rect {
    val safeTop = origin.y + statusTop + gap
    val safeBottom = origin.y + screenHeight - bottomInset - gap
    if (row.height + gap + desiredHeight > safeBottom - safeTop) return row
    val top = minOf(row.top, safeBottom - desiredHeight - gap - row.height)
        .coerceAtLeast(safeTop)
    return row.translate(Offset(0f, top - row.top))
}

internal fun rowMenuPlacement(
    row: Rect, origin: Offset, panel: IntSize, screenWidth: Float,
    statusTop: Float, edgePadding: Float, gap: Float, lift: Float, above: Boolean,
): TabMenuPlacement {
    val base = tabMenuPlacement(row, origin, panel, screenWidth, statusTop,
        edgePadding, gap, lift, above)
    val left = (row.left - origin.x).coerceIn(edgePadding,
        (screenWidth - panel.width - edgePadding).coerceAtLeast(edgePadding))
    return base.copy(left = left, pivotX = 0f)
}

/** Tabs always open above; title menus prefer below and fall back above only when needed. */
internal fun contextMenuSpace(
    anchor: Rect, origin: Offset, screenHeight: Float, statusTop: Float, bottomInset: Float,
    gap: Float, lift: Float, desiredHeight: Float, tabAnchor: Boolean,
): ContextMenuSpace {
    val above = (anchor.top - origin.y - statusTop - gap * 2 - lift).coerceAtLeast(0f)
    val below = (screenHeight - bottomInset - gap * 2 - anchor.bottom + origin.y - lift).coerceAtLeast(0f)
    val useAbove = tabAnchor || below < desiredHeight
    return ContextMenuSpace(useAbove, if (useAbove) above else below)
}
