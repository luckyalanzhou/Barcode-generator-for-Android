package com.luckyalanzhou.barcodegenerator

import android.graphics.Color

/** 条码图像专用高对比度配色，由外观的深浅状态选择。 */
internal object BarcodeImageColors {
    fun foreground(dark: Boolean): Int = if (dark) Color.rgb(17, 19, 24) else Color.BLACK

    fun background(dark: Boolean): Int = if (dark) Color.rgb(241, 243, 246) else Color.WHITE
}
