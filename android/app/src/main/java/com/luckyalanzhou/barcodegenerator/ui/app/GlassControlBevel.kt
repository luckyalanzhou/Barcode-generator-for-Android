package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** One contour for the resting optical edge; Tab and circular actions use the same material. */
internal fun DrawScope.drawGlassControlBevel(
    topLeft: Offset,
    bounds: Size,
    cornerRadius: Float,
    material: TabGlassMaterial,
    background: Color,
    accent: Color,
    density: Float,
    highContrast: Boolean,
) {
    val stroke = tabGlassEdgeWidthPx(material, density)
    val inset = topLeft + Offset(stroke * .5f, stroke * .5f)
    val innerSize = Size((bounds.width - stroke).coerceAtLeast(.1f), (bounds.height - stroke).coerceAtLeast(.1f))
    val corner = CornerRadius((cornerRadius - stroke * .5f).coerceAtLeast(.1f))
    drawRoundRect(tabGlassOutlineColor(background, material), inset, innerSize, corner, style = Stroke(stroke))
    if (highContrast) {
        drawRoundRect(accent, inset, innerSize, corner, style = Stroke(stroke.coerceAtLeast(density)))
        return
    }
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = material.rimLight),
            .30f to Color.White.copy(alpha = material.rimLight * .12f),
            .55f to Color.Transparent,
            1f to Color.Black.copy(alpha = material.innerShadow),
            startY = topLeft.y, endY = topLeft.y + bounds.height,
        ),
        inset, innerSize, corner, style = Stroke(stroke),
    )
}
