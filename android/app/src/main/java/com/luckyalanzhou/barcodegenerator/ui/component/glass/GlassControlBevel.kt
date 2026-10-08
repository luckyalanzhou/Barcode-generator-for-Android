package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** A narrow inward bevel and its crest; the center and foreground remain clear. */
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
    // Confine the reflection to an inward band, not a full-face gray gradient or outer halo.
    // A shared outer boundary keeps the crest and bevel from looking like separate frames.
    val band = (1.8f * density).coerceAtMost(bounds.minDimension * .12f).coerceAtLeast(stroke)
    val bandInset = topLeft + Offset(band * .5f, band * .5f)
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = material.rimLight * .24f),
            .18f to Color.White.copy(alpha = material.rimLight * .06f),
            .45f to Color.Transparent,
            .72f to Color.Black.copy(alpha = material.innerShadow * .16f),
            .90f to Color.Black.copy(alpha = material.innerShadow * .65f),
            1f to Color.White.copy(alpha = material.rimLight * .13f),
            startY = topLeft.y, endY = topLeft.y + bounds.height,
        ),
        bandInset,
        Size((bounds.width - band).coerceAtLeast(.1f), (bounds.height - band).coerceAtLeast(.1f)),
        CornerRadius((cornerRadius - band * .5f).coerceAtLeast(.1f)),
        style = Stroke(band),
    )
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
