package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection

/** 结果页静态按钮使用的细外轮廓与窄内侧边缘高光。 */
internal data class ResultActionRimColors(
    val outline: Color,
    val top: Color,
    val side: Color,
    val bottom: Color,
)

internal fun resultActionRimColors(background: Color): ResultActionRimColors {
    val dark = background.luminance() < .35f
    return ResultActionRimColors(
        outline = if (dark) Color.White.copy(alpha = .22f) else Color.Black.copy(alpha = .16f),
        top = Color.White.copy(alpha = if (dark) .46f else .85f),
        side = Color.White.copy(alpha = if (dark) .16f else .38f),
        bottom = Color.White.copy(alpha = if (dark) .23f else .26f),
    )
}

internal fun resultActionRimStroke(density: Float, highContrast: Boolean): Float {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return if (highContrast) safeDensity.coerceIn(1f, 4f) else (safeDensity * .55f).coerceIn(1f, 1.5f)
}

/** 完整轮廓与内侧高光分开绘制，全部向内收进，避免裁剪掉半根描边。 */
internal fun DrawScope.drawResultActionRim(background: Color, accent: Color, density: Float, highContrast: Boolean) {
    val palette = resultActionRimColors(background)
    val stroke = resultActionRimStroke(density, highContrast)
    val radius = (size.minDimension * .5f - stroke * .5f - .5f).coerceAtLeast(0f)
    drawCircle(if (highContrast) accent else palette.outline, radius, style = Stroke(stroke))
    if (highContrast || radius <= stroke) return

    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val band = (safeDensity * .7f).coerceIn(1f, 3f)
    val innerRadius = (radius - stroke * .5f - band * .5f).coerceAtLeast(0f)
    // 每个方向都有非零高光，但顶部更亮；不铺满内部，不增加外圈光晕。
    drawCircle(
        Brush.verticalGradient(
            0f to palette.top,
            .5f to palette.side,
            1f to palette.bottom,
            startY = center.y - innerRadius,
            endY = center.y + innerRadius,
        ),
        innerRadius,
        center = Offset(size.width * .5f, size.height * .5f),
        style = Stroke(band),
    )
}

/** 将同一套双层细边缘绘制到调用方现有胶囊或圆角矩形范围内。 */
internal fun DrawScope.drawResultActionRim(
    shape: Shape,
    layoutDirection: LayoutDirection,
    background: Color,
    accent: Color,
    density: Float,
    highContrast: Boolean,
    showInnerHighlight: Boolean = true,
    origin: Offset = Offset.Zero,
    bounds: Size = size,
) {
    if (!bounds.width.isFinite() || !bounds.height.isFinite() || bounds.width <= 0f || bounds.height <= 0f) return
    val palette = resultActionRimColors(background)
    val stroke = resultActionRimStroke(density, highContrast)
    val outerInset = stroke * .5f + .5f
    val outerSize = Size(
        (bounds.width - outerInset * 2f).coerceAtLeast(.1f),
        (bounds.height - outerInset * 2f).coerceAtLeast(.1f),
    )
    val outerOutline = shape.createOutline(outerSize, layoutDirection, this)
    translate(origin.x + outerInset, origin.y + outerInset) {
        drawOutline(
            outerOutline,
            if (highContrast) accent else palette.outline,
            style = Stroke(stroke),
        )
    }
    if (highContrast || !showInnerHighlight) return

    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val band = (safeDensity * .7f).coerceIn(1f, 3f)
    val bandInset = stroke + band * .5f + .5f
    val bandSize = Size(
        (bounds.width - bandInset * 2f).coerceAtLeast(.1f),
        (bounds.height - bandInset * 2f).coerceAtLeast(.1f),
    )
    val bandOutline = shape.createOutline(bandSize, layoutDirection, this)
    val gradient = Brush.verticalGradient(
        0f to palette.top,
        .5f to palette.side,
        1f to palette.bottom,
        startY = 0f,
        endY = bandSize.height,
    )
    translate(origin.x + bandInset, origin.y + bandInset) {
        drawOutline(bandOutline, gradient, style = Stroke(band))
    }
}
