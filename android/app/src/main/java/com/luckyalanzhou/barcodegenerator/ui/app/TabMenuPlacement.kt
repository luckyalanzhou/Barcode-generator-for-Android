package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

internal data class TabMenuPlacement(val left: Float, val top: Float, val pivotX: Float, val anchorCenterX: Float)

/** 测量逻辑与回归测试共用的物理像素几何；上移量同时应用于两个前景层。 */
internal fun tabMenuPlacement(
    anchor: Rect, origin: Offset, panel: IntSize, screenWidth: Float,
    statusTop: Float, edgePadding: Float, gap: Float, lift: Float,
    above: Boolean = true, alignTabEdge: Boolean = false,
): TabMenuPlacement {
    val center = anchor.center.x - origin.x
    // Tab 按整个槽位边缘对齐；其他来源保持原本的中心定位。
    val desiredLeft = if (!alignTabEdge) center - panel.width / 2f
        else if (center < screenWidth / 2f) anchor.left - origin.x
        else anchor.right - origin.x - panel.width
    val left = desiredLeft.coerceIn(edgePadding,
        (screenWidth - panel.width - edgePadding).coerceAtLeast(edgePadding))
    val top = if (above) (anchor.top - origin.y - panel.height - gap).coerceAtLeast(statusTop + gap + lift)
        else anchor.bottom - origin.y + gap + lift
    val pivot = if (panel.width > 0) ((center - left) / panel.width).coerceIn(0f, 1f) else .5f
    return TabMenuPlacement(left, top, pivot, center)
}

internal data class ContextMenuSpace(val above: Boolean, val height: Float)

/** 保持列表项与菜单锚点相对位置不变；通过上移来源卡片为下方菜单腾出空间。 */
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

/** Tab 菜单始终显示在上方；标题菜单优先显示在下方，空间不足时再改为上方。 */
internal fun contextMenuSpace(
    anchor: Rect, origin: Offset, screenHeight: Float, statusTop: Float, bottomInset: Float,
    gap: Float, lift: Float, desiredHeight: Float, tabAnchor: Boolean,
): ContextMenuSpace {
    val above = (anchor.top - origin.y - statusTop - gap * 2 - lift).coerceAtLeast(0f)
    val below = (screenHeight - bottomInset - gap * 2 - anchor.bottom + origin.y - lift).coerceAtLeast(0f)
    val useAbove = tabAnchor || below < desiredHeight
    return ContextMenuSpace(useAbove, if (useAbove) above else below)
}
