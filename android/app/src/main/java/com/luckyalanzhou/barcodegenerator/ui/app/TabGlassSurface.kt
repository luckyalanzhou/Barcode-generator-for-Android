package com.luckyalanzhou.barcodegenerator.ui.app

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

/** Compatible material draws behind unmodified glyphs; no per-frame layout or CPU snapshots. */
@Composable
internal fun TabGlassSurface(
    frameProvider: () -> TabGlassFrame,
    material: TabGlassMaterial,
    accent: Color,
    background: Color,
    highContrast: Boolean = false,
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
        // Both passes share one contour: no offset second frame and no outside halo.
        drawRoundRect(tabGlassOutlineColor(background, material), innerTopLeft, innerBounds,
            innerCorner, style = Stroke(stroke))
        drawRoundRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = material.rimLight),
                .30f to Color.White.copy(alpha = material.rimLight * .12f),
                .55f to Color.Transparent,
                1f to Color.Black.copy(alpha = material.innerShadow),
                startY = topLeft.y, endY = topLeft.y + bounds.height,
            ),
            innerTopLeft, innerBounds, innerCorner, style = Stroke(stroke),
        )
        if (highContrast) {
            drawRoundRect(accent, innerTopLeft, innerBounds, innerCorner, style = Stroke(stroke.coerceAtLeast(frame.density)))
        }
        if (frame.motion > .001f) {
            val dx = frame.touchX - frame.centerX
            val dy = frame.touchY - frame.centerY
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.001f)
            val light = Offset(frame.centerX + frame.halfWidth * dx / length, frame.centerY + frame.halfHeight * dy / length)
            drawRoundRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = (material.rimLight * frame.motion * 1.45f).coerceAtMost(1f)), Color.Transparent),
                    center = light, radius = frame.halfHeight * (.6f + frame.contactSpread * .5f),
                ),
                innerTopLeft, innerBounds, innerCorner, style = Stroke(stroke),
            )
        }
    })
}
