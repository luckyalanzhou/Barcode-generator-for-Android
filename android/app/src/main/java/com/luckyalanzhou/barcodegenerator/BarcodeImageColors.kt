package com.luckyalanzhou.barcodegenerator

import android.graphics.Color

/** 条码图像专用高对比度配色，由外观的深浅状态选择。 */
internal object BarcodeImageColors {
    fun foreground(dark: Boolean): Int = if (dark) Color.rgb(17, 19, 24) else Color.BLACK

    // 与全局浅色页面背景一致，避免每个条码 Bitmap 在页面上显出白色方框；
    // 该颜色仍然足够明亮，保持条码前景与背景的可识别对比度。
    fun background(dark: Boolean): Int = if (dark) Color.rgb(241, 243, 246) else Color.rgb(242, 242, 247)
}
