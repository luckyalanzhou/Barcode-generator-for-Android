package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.sin

/** Tab 场景局部坐标中的物理像素尺寸，供着色器和兼容绘制共用。 */
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

/** 静态玻璃仍会轻微折射页面背景，图标与文字始终保持清晰。 */
internal fun tabBackdropRefractionDp(frame: TabGlassFrame): Float {
    if (!frame.density.isFinite() || frame.density <= 0f || !frame.refractionPx.isFinite()) return 0f
    val moving = (frame.refractionPx / frame.density).coerceIn(0f, GlassControlDefaults.MaxBackdropRefractionDp)
    val activity = frame.motion.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
// 静止时只保留轻微透镜效果，移动时也只使用部分动态强度。
// 类似 Apple 的玻璃首先应呈现清晰的材质边缘；折射用于辅助运动，
// 不应盖过下方内容。
    val dynamic = moving * (.45f + .25f * activity)
    return (GlassControlDefaults.TabRestRefractionDp + dynamic)
        .coerceIn(GlassControlDefaults.TabRestRefractionDp, GlassControlDefaults.MaxBackdropRefractionDp)
}

/** 拖动可从当前 Tab 的整个区域开始，不局限于玻璃胶囊范围。 */
internal fun tabSelectedCellContains(
    width: Float,
    height: Float,
    tabCount: Int,
    selectedTabIndex: Int,
    point: androidx.compose.ui.geometry.Offset,
): Boolean {
    if (tabCount <= 0 || selectedTabIndex !in 0 until tabCount ||
        !width.isFinite() || width <= 0f || !height.isFinite() || height <= 0f ||
        !point.x.isFinite() || !point.y.isFinite() ||
        point.x < 0f || point.x >= width || point.y < 0f || point.y > height
    ) return false
    val cellWidth = width / tabCount
    val left = cellWidth * selectedTabIndex
    val right = if (selectedTabIndex == tabCount - 1) width else cellWidth * (selectedTabIndex + 1)
    return point.x >= left && point.x < right
}

/** 将胶囊边界转换为单个 Tab 区域内的局部绘制坐标。 */
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
        travelStrength = opticalStrength * speed,
    )
}
