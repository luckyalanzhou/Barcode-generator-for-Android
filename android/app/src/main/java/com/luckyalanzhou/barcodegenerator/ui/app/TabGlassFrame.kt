package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.runtime.Immutable
import kotlin.math.sin
import kotlin.math.abs

/** Physical pixels in the tab scene's local coordinates; shared by shader and compatible drawing. */
@Immutable
internal data class TabGlassFrame(
    val width: Float,
    val height: Float,
    val centerX: Float,
    val centerY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val motion: Float,
    val refractionPx: Float,
    val density: Float,
    val touchX: Float,
    val touchY: Float,
    val contactSpread: Float,
)

internal fun tabGlassFrame(
    width: Float,
    height: Float,
    density: Float,
    tabCount: Int,
    progress: Float,
    motion: Float,
    impact: Float,
    direction: Float,
    touchX: Float? = null,
    touchY: Float? = null,
    contactSpread: Float = 1f,
    refractionDp: Float = 2.25f,
    velocityTabsPerSecond: Float = 4f,
): TabGlassFrame {
    val safeWidth = width.coerceAtLeast(1f)
    val safeHeight = height.coerceAtLeast(1f)
    val safeDensity = density.coerceAtLeast(.1f)
    val safeProgress = progress.coerceIn(0f, (tabCount.coerceAtLeast(1) - 1).toFloat())
    val strength = motion.coerceIn(0f, 1f)
    val speed = if (velocityTabsPerSecond.isFinite()) (abs(velocityTabsPerSecond) / 4f).coerceIn(0f, 1f) else 0f
    val opticalStrength = strength * (.50f + .50f * speed)
    val fraction = safeProgress - safeProgress.toInt()
    val handoff = sin(fraction * Math.PI).toFloat().coerceIn(0f, 1f) * strength * (.45f + .55f * speed)
    val cellWidth = safeWidth / tabCount.coerceAtLeast(1)
    val halfWidth = ((cellWidth * .5f - 3f * safeDensity).coerceAtLeast(1f) *
        (1f + impact.coerceIn(0f, .035f) + handoff * .05f)).coerceAtMost(safeWidth * .5f)
    val halfHeight = (safeHeight * .5f - 2f * safeDensity).coerceAtLeast(.5f) *
        (1f - handoff * .03f)
    val centerX = (cellWidth * (safeProgress + .5f)).coerceIn(halfWidth, safeWidth - halfWidth)
    val centerY = safeHeight * .5f
    return TabGlassFrame(
        width = safeWidth,
        height = safeHeight,
        centerX = centerX,
        centerY = centerY,
        halfWidth = halfWidth,
        halfHeight = halfHeight.coerceAtMost(safeHeight * .5f),
        motion = opticalStrength,
        refractionPx = refractionDp.coerceIn(0f, 3f) * safeDensity * opticalStrength,
        density = safeDensity,
        touchX = touchX ?: centerX + halfWidth * .6f * direction.coerceIn(-1f, 1f),
        touchY = touchY ?: centerY - halfHeight * .75f,
        contactSpread = contactSpread.coerceIn(0f, 1f),
    )
}
