package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection

/** 全局控件共用的静态边缘材质：窄高光叠在对比轮廓上，随背景切换明暗。 */
internal data class ResultActionRimColors(
    val outline: Color,
    val top: Color,
    val side: Color,
    val bottom: Color,
)

internal fun resultActionRimColors(background: Color): ResultActionRimColors {
    val dark = background.luminance() < .35f
    return ResultActionRimColors(
        // 先用对比描边从背景中分离轮廓，再叠窄高光；浅色底部略压暗，避免白线融入白底。
        outline = if (dark) Color.White.copy(alpha = .30f) else Color.Black.copy(alpha = .22f),
        top = Color.White.copy(alpha = if (dark) .82f else .98f),
        side = if (dark) Color.White.copy(alpha = .34f) else Color.Black.copy(alpha = .06f),
        bottom = if (dark) Color.White.copy(alpha = .40f) else Color.Black.copy(alpha = .13f),
    )
}

internal fun resultActionRimStroke(density: Float, highContrast: Boolean): Float {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return if (highContrast) safeDensity.coerceIn(1f, 4f) else (safeDensity * .55f).coerceIn(1f, 1.5f)
}

/** 圆形玻璃按钮也使用一条垂直渐变描边，避免外圈与内圈叠成双边。 */
internal fun DrawScope.drawResultActionRim(
    background: Color,
    accent: Color,
    density: Float,
    highContrast: Boolean,
    edgeHighlight: Boolean = true,
) {
    drawResultActionRim(
        shape = CircleShape,
        layoutDirection = LayoutDirection.Ltr,
        background = background,
        accent = accent,
        density = density,
        highContrast = highContrast,
        edgeHighlight = edgeHighlight,
        bounds = size,
    )
}

/** 将同一条细渐变边缘绘制到调用方现有胶囊或圆角矩形范围内。 */
internal fun DrawScope.drawResultActionRim(
    shape: Shape,
    layoutDirection: LayoutDirection,
    background: Color,
    accent: Color,
    density: Float,
    highContrast: Boolean,
    edgeHighlight: Boolean = true,
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
    val rim = when {
        highContrast -> SolidColor(accent)
        edgeHighlight -> Brush.verticalGradient(
            0f to palette.top,
            .5f to palette.side,
            1f to palette.bottom,
            startY = 0f,
            endY = bounds.height.coerceAtLeast(1f),
        )
        else -> SolidColor(palette.outline)
    }
    val outerOutline = shape.createOutline(outerSize, layoutDirection, this)
    translate(origin.x + outerInset, origin.y + outerInset) {
        if (!highContrast && edgeHighlight) {
            // 基础轮廓与内侧高光共用同一条几何线，不增大控件、阴影或触摸范围。
            drawOutline(outerOutline, SolidColor(palette.outline), style = Stroke(stroke))
            drawOutline(outerOutline, rim, style = Stroke(stroke * .62f))
        } else {
            drawOutline(outerOutline, rim, style = Stroke(stroke))
        }
    }
}
