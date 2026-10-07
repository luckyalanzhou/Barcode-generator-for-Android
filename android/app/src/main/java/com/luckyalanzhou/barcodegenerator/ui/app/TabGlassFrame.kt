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
    val travelStrength: Float = 0f,
)

/** Static glass still bends the page slightly, never the resting glyphs above it. */
internal fun tabBackdropRefractionDp(frame: TabGlassFrame): Float {
    if (!frame.density.isFinite() || frame.density <= 0f || !frame.refractionPx.isFinite()) return 0f
    val moving = (frame.refractionPx / frame.density).coerceIn(0f, GlassControlDefaults.MaxBackdropRefractionDp)
    val activity = frame.motion.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return (GlassControlDefaults.TabRestRefractionDp * (1f - activity) + moving)
        .coerceIn(0f, GlassControlDefaults.MaxBackdropRefractionDp)
}

/** Test the rendered capsule, not the whole navigation bar or the selected tab's cell. */
internal fun tabCapsuleContains(frame: TabGlassFrame, point: androidx.compose.ui.geometry.Offset): Boolean {
    if (!point.x.isFinite() || !point.y.isFinite()) return false
    val radius = minOf(frame.halfWidth, frame.halfHeight)
    val dx = (abs(point.x - frame.centerX) - (frame.halfWidth - radius)).coerceAtLeast(0f)
    val dy = (abs(point.y - frame.centerY) - (frame.halfHeight - radius)).coerceAtLeast(0f)
    return dx * dx + dy * dy <= radius * radius
}

/** Capsule bounds translated into one tab cell's local drawing coordinates. */
internal fun tabGlassCapsuleBoundsInTab(
    frame: TabGlassFrame,
    tabIndex: Int,
    tabCount: Int,
): androidx.compose.ui.geometry.Rect? {
    if (tabCount <= 0 || tabIndex !in 0 until tabCount) return null
    val cellWidth = frame.width / tabCount
    return androidx.compose.ui.geometry.Rect(
        left = frame.centerX - frame.halfWidth - tabIndex * cellWidth,
        top = frame.centerY - frame.halfHeight,
        right = frame.centerX + frame.halfWidth - tabIndex * cellWidth,
        bottom = frame.centerY + frame.halfHeight,
    )
}

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
    val opticalStrength = strength * (.80f + .20f * speed)
    val fraction = safeProgress - safeProgress.toInt()
    val handoff = sin(fraction * Math.PI).toFloat().coerceIn(0f, 1f) * strength * (.45f + .55f * speed)
    val cellWidth = safeWidth / tabCount.coerceAtLeast(1)
    val strain = tabContactStrain(touchX, cellWidth * (safeProgress + .5f), cellWidth, strength)
    val halfWidth = ((cellWidth * .5f - 3f * safeDensity).coerceAtLeast(1f) *
        (1f + impact.coerceIn(0f, .035f) + handoff * .05f + strain)).coerceAtMost(safeWidth * .5f)
    val halfHeight = (safeHeight * .5f - 2f * safeDensity).coerceAtLeast(.5f) *
        (1f - handoff * .03f - strain * .65f)
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
        refractionPx = refractionDp.coerceIn(0f, GlassControlDefaults.MaxBackdropRefractionDp) * safeDensity * opticalStrength,
        density = safeDensity,
        touchX = touchX ?: centerX + halfWidth * .6f * direction.coerceIn(-1f, 1f),
        touchY = touchY ?: centerY - halfHeight * .75f,
        contactSpread = contactSpread.coerceIn(0f, 1f),
        travelStrength = opticalStrength * (if (velocityTabsPerSecond.isFinite())
            (abs(velocityTabsPerSecond) / .06f).coerceIn(0f, 1f) else 0f),
    )
}
