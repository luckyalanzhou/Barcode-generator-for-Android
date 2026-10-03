package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/** Tab-local material rendering; gestures, navigation and menu semantics stay in the tab bar. */
@Composable
internal fun TabGlassSurface(
    progress: Float,
    selectedIndex: Int,
    inDrag: Boolean,
    impact: Float,
    direction: Float,
    tabCount: Int,
    dark: Boolean,
    accent: Color,
) {
    val indicatorShape = remember { RoundedCornerShape(50) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tabWidth = maxWidth / tabCount
        val indicatorOffset = tabWidth * progress
        val indicatorInMotion = inDrag || abs(progress - selectedIndex) > .01f
        val fraction = progress - progress.toInt().toFloat()
        val tabHandoff = if (indicatorInMotion) sin(fraction * Math.PI).toFloat().coerceIn(0f, 1f) else 0f
        val surfaceFlowPosition = (progress / (tabCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(
                // Keep the liquid-glass treatment scoped to the selected capsule, not the full rail.
                modifier = Modifier.offset(x = indicatorOffset).width(tabWidth).fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        // The capsule stretches while handing off between tabs, rather than
                        // relying only on the brief pulse emitted at a tab-center crossing.
                        scaleX = 1f + impact +
                            if (indicatorInMotion) .025f + tabHandoff * .075f else 0f
                        scaleY = 1f - if (indicatorInMotion) .012f + tabHandoff * .045f
                            else impact * .12f
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (direction > 0f) 0f else 1f,
                            pivotFractionY = .5f,
                        )
                    }
                    .shadow(
                        elevation = if (dark) .35.dp else 1.25.dp,
                        shape = indicatorShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = if (dark) .08f else .045f),
                        spotColor = Color.Black.copy(alpha = if (dark) .10f else .07f),
                    )
                    .drawBehind {
                        val outline = .65.dp.toPx()
                        val corner = CornerRadius(size.height / 2f)
                        val fill = if (dark) {
                            listOf(
                                Color.White.copy(alpha = .105f),
                                Color.White.copy(alpha = .065f),
                                accent.copy(alpha = .045f),
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = .78f),
                                accent.copy(alpha = .075f),
                                Color.White.copy(alpha = .56f),
                            )
                        }
                        val rim = if (dark) {
                            listOf(Color.White.copy(alpha = .19f), Color.White.copy(alpha = .075f))
                        } else {
                            listOf(Color.White.copy(alpha = .76f), accent.copy(alpha = .16f))
                        }
                        val flowCenterX = size.width * (.15f + surfaceFlowPosition * .70f)
                        drawRoundRect(
                            brush = Brush.verticalGradient(fill),
                            cornerRadius = corner,
                        )
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (dark) .13f else .30f),
                                    Color.White.copy(alpha = if (dark) .045f else .09f),
                                    Color.Transparent,
                                ),
                                center = Offset(flowCenterX, size.height * .12f),
                                radius = size.height * 1.15f,
                            ),
                            cornerRadius = corner,
                        )
                        if (indicatorInMotion) {
                            // A soft specular lens travels across the glass surface with the
                            // capsule; the glyphs receive only a separate, subtle parallax response.
                            val lensHalfWidth = size.width * .22f
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = if (dark) .105f else .19f),
                                        accent.copy(alpha = if (dark) .045f else .075f),
                                        Color.Transparent,
                                    ),
                                    startX = (flowCenterX - lensHalfWidth).coerceAtLeast(0f),
                                    endX = (flowCenterX + lensHalfWidth).coerceAtMost(size.width),
                                ),
                                cornerRadius = corner,
                            )
                        }
                        drawRoundRect(
                            brush = Brush.verticalGradient(rim),
                            topLeft = Offset(outline / 2f, outline / 2f),
                            size = androidx.compose.ui.geometry.Size(size.width - outline, size.height - outline),
                            cornerRadius = CornerRadius((size.height - outline) / 2f),
                            style = Stroke(width = outline),
                        )
                    }
            )
    }
}
