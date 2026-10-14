package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance

/** 结果页专用静态边缘，不改 Tab 的方向性高光。 */
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
