package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min
import kotlin.math.sqrt

/** 兼容材质绘制在原样图标和文字的下方；不逐帧重新布局，也不创建 CPU 像素快照。 */
@Composable
internal fun TabGlassSurface(
    frameProvider: () -> TabGlassFrame,
    material: TabGlassMaterial,
    accent: Color,
    background: Color,
    highContrast: Boolean = false,
    drawMotionHighlight: Boolean = true,
) {
    Box(Modifier.fillMaxSize().drawBehind {
        val frame = frameProvider()
        val topLeft = Offset(frame.centerX - frame.halfWidth, frame.centerY - frame.halfHeight)
        val bounds = Size(frame.halfWidth * 2, frame.halfHeight * 2)
        val corner = CornerRadius(min(frame.halfWidth, frame.halfHeight))
        val fill = tabGlassFill(background, accent, material)
        drawRoundRect(fill.copy(alpha = material.surfaceOpacity), topLeft, bounds, corner)
        val stroke = tabGlassEdgeWidthPx(material, frame.density)
        val innerTopLeft = topLeft + Offset(stroke * .5f, stroke * .5f)
        val innerBounds = Size((bounds.width - stroke).coerceAtLeast(.1f), (bounds.height - stroke).coerceAtLeast(.1f))
        val innerCorner = CornerRadius((corner.x - stroke * .5f).coerceAtLeast(.1f))
        // 选中框沿用结果页按钮的完整轮廓与窄内侧高光；不改变胶囊尺寸或其玻璃填充。
        drawResultActionRim(
            shape = CircleShape,
            layoutDirection = layoutDirection,
            background = background,
            accent = accent,
            density = frame.density,
            highContrast = highContrast,
            origin = topLeft,
            bounds = bounds,
        )
        if (drawMotionHighlight && frame.motion > .001f) {
            val dx = frame.touchX - frame.centerX
            val dy = frame.touchY - frame.centerY
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.001f)
            val light = Offset(frame.centerX + frame.halfWidth * dx / length, frame.centerY + frame.halfHeight * dy / length)
            drawRoundRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = (material.rimLight * frame.motion * .60f).coerceAtMost(1f)), Color.Transparent),
                    center = light, radius = frame.halfHeight * (.72f + frame.contactSpread * .28f),
                ),
                innerTopLeft, innerBounds, innerCorner, style = Stroke(stroke),
            )
        }
    })
}
