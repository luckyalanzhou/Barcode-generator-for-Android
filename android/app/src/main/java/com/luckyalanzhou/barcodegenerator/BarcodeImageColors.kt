package com.luckyalanzhou.barcodegenerator

import android.graphics.Color

/** 条码始终保持扫描枪兼容的深色条纹；深色页面仅改变浅色静区的色值。 */
internal object BarcodeImageColors {
    fun foreground(dark: Boolean): Int = if (dark) Color.rgb(17, 19, 24) else Color.BLACK

    // 深色页面中的条码仍需浅色静区，避免形成扫描枪难以识别的反色条码。
    fun background(dark: Boolean): Int = if (dark) Color.rgb(241, 243, 246) else Color.rgb(242, 242, 247)
}
