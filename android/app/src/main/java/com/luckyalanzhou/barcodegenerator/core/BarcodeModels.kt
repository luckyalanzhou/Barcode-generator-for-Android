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

data class CodeItem(
    val id: Long,
    var text: String,
    var format: String,
    var createdAt: Long = System.currentTimeMillis(),
    var favorite: Boolean = false,
    var folder: String = "默认",
    var inHistory: Boolean = true
)

data class FavoriteGroup(
    val id: Long,
    var folder: String,
    var name: String,
    val savedAt: Long,
    var itemIds: MutableList<Long>
)
