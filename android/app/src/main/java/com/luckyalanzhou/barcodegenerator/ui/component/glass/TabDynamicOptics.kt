package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** 色散只按物理像素计算；不重着色或重放前景图标和文字。 */
internal data class TabDynamicOptics(val dispersionPx: Float, val edgeColorStrength: Float)

internal fun tabDynamicOptics(frame: TabGlassFrame): TabDynamicOptics {
    val strength = if (frame.travelStrength.isFinite()) frame.travelStrength.coerceIn(0f, 1f) else 0f
    return TabDynamicOptics(1.25f * strength, .08f * strength)
}

/** 由触控驱动的轻微形变，供表面、背景遮罩和前景透镜共用。 */
internal fun tabContactStrain(touchX: Float?, centerX: Float, cellWidth: Float, motion: Float): Float {
    if (touchX == null || !touchX.isFinite() || !cellWidth.isFinite() || cellWidth <= 0f) return 0f
    return (kotlin.math.abs(touchX - centerX) / (cellWidth * .5f)).coerceIn(0f, 1f) *
        motion.coerceIn(0f, 1f) * .02f
}
