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
import androidx.compose.ui.graphics.lerp
import kotlin.math.min
import kotlin.math.sqrt

/** Compatible material draws behind unmodified glyphs; no per-frame layout or CPU snapshots. */
@Composable
internal fun TabGlassSurface(
    frameProvider: () -> TabGlassFrame,
    dark: Boolean,
    accent: Color,
    background: Color,
) {
    Box(Modifier.fillMaxSize().drawBehind {
        val frame = frameProvider()
        val topLeft = Offset(frame.centerX - frame.halfWidth, frame.centerY - frame.halfHeight)
        val bounds = Size(frame.halfWidth * 2, frame.halfHeight * 2)
        val corner = CornerRadius(min(frame.halfWidth, frame.halfHeight))
        val fill = lerp(lerp(background, Color.White, if (dark) .055f else .18f), accent, .035f)
        drawRoundRect(fill, topLeft, bounds, corner)
        val stroke = .65f * frame.density
        val innerTopLeft = topLeft + Offset(stroke * .5f, stroke * .5f)
        val innerBounds = Size((bounds.width - stroke).coerceAtLeast(.1f), (bounds.height - stroke).coerceAtLeast(.1f))
        val innerCorner = CornerRadius((corner.x - stroke * .5f).coerceAtLeast(.1f))
        drawRoundRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (dark) .16f else .22f), Color.Black.copy(alpha = if (dark) .10f else .07f)),
                startY = topLeft.y, endY = topLeft.y + bounds.height,
            ),
            innerTopLeft, innerBounds, innerCorner, style = Stroke(stroke),
        )
        if (frame.motion > .001f) {
            val dx = frame.touchX - frame.centerX
            val dy = frame.touchY - frame.centerY
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.001f)
            val light = Offset(frame.centerX + frame.halfWidth * dx / length, frame.centerY + frame.halfHeight * dy / length)
            drawRoundRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .18f * frame.motion), Color.Transparent),
                    center = light, radius = frame.halfHeight * (.6f + frame.contactSpread * .5f),
                ),
                innerTopLeft, innerBounds, innerCorner, style = Stroke(stroke),
            )
        }
    })
}
