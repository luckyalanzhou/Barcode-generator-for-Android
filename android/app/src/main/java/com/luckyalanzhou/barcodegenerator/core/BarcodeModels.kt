package com.luckyalanzhou.barcodegenerator

import android.graphics.Color

data class StyleSettings(
    var barColor: Int = Color.BLACK,
    var bgColor: Int = Color.WHITE,
    var showText: Boolean = true,
    var textPosition: String = "bottom",
    var textSize: Float = 14f,
    var barHeight: Int = 55,
    var barWidth: Float = 220f,
    var margin: Int = 4,
    var showFormat: Boolean = false,
    var colorScheme: String = "system"
)
