package com.luckyalanzhou.barcodegenerator.ui.theme

/** 非颜色、非尺寸的视觉效果参数，与调色板保持独立。 */
internal data class AppVisualEffects(
    val primaryBorderAlpha: Float,
    val dialogDimAmount: Float,
)

internal fun appVisualEffects(dark: Boolean): AppVisualEffects =
    if (dark) DarkAppVisualEffects else LightAppVisualEffects

private val LightAppVisualEffects = AppVisualEffects(
    primaryBorderAlpha = .22f,
    dialogDimAmount = .34f,
)

private val DarkAppVisualEffects = AppVisualEffects(
    primaryBorderAlpha = .32f,
    dialogDimAmount = .48f,
)
