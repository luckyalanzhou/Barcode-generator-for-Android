package com.luckyalanzhou.barcodegenerator

private const val DEFAULT_BAR_COLOR = -0x1000000
private const val DEFAULT_BACKGROUND_COLOR = -0x1

/** 条码显示设置；保持为纯领域数据，不把 Android Color 带入 domain 模块。 */
data class StyleSettings(
    var barColor: Int = DEFAULT_BAR_COLOR,
    var bgColor: Int = DEFAULT_BACKGROUND_COLOR,
    var showText: Boolean = true,
    var textPosition: String = "bottom",
    var textSize: Float = 14f,
    var barHeight: Int = 55,
    var barWidth: Float = 220f,
    var margin: Int = 4,
    var showFormat: Boolean = false,
    var colorScheme: String = "system"
)

