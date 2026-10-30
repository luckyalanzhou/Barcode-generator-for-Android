package com.luckyalanzhou.barcodegenerator.domain

import kotlin.math.roundToInt

/** 屏幕、缓存与导出共用的原始条码尺寸规则，不依赖页面状态或 Android。 */
fun barcodeRenderSize(formatId: String, style: StyleSettings, density: Float): Pair<Int, Int> {
    require(density.isFinite() && density > 0)
    return if (formatId == "code128") {
        (style.barWidth.roundToInt().coerceIn(120, 300) * density).roundToInt().coerceAtLeast(1) to
            (style.barHeight.coerceIn(30, 80) * density).roundToInt().coerceAtLeast(1)
    } else 500 to if (formatId == "qr") 500 else 200
}
