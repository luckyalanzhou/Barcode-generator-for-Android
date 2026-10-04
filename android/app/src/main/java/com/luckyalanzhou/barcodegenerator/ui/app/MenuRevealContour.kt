package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Grow the visible contour from its anchor; layout and glyph sizes remain unchanged. */
internal fun menuRevealBounds(size: Size, pivotX: Float, above: Boolean, reveal: MenuGlassReveal): Rect {
    val width = size.width * reveal.scale
    val height = size.height * (.2f + .8f * reveal.thickness)
    val left = (size.width - width) * pivotX.coerceIn(0f, 1f)
    val top = if (above) size.height - height else 0f
    return Rect(left, top, left + width, top + height)
}

internal fun menuRevealPath(bounds: Rect, radiusPx: Float): Path = Path().apply {
    val radius = radiusPx.coerceAtMost(minOf(bounds.width, bounds.height) / 2f).coerceAtLeast(0f)
    addRoundRect(RoundRect(bounds, CornerRadius(radius)))
}

internal class MenuRevealContour(
    private val pivotX: Float,
    private val above: Boolean,
    private val reveal: MenuGlassReveal,
    private val radiusPx: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(menuRevealPath(menuRevealBounds(size, pivotX, above, reveal), radiusPx))
}
