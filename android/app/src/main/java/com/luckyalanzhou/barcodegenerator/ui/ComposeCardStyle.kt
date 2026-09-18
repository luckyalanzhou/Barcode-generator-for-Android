package com.luckyalanzhou.barcodegenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 全局卡片基座：统一圆角、边缘分离度和轻量阴影，页面保留自己的表面颜色。 */
internal fun Modifier.globalCardSurface(
    dark: Boolean,
    color: Color,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    elevation: Dp = 3.dp,
): Modifier = this
    .shadow(elevation, shape, clip = false)
    .clip(shape)
    .background(color)
    .border(
        1.dp,
        if (dark) Color.White.copy(alpha = 0.10f) else Color(0xffd9e1ec).copy(alpha = 0.82f),
        shape,
    )

/** 按钮统一的轻量浮起效果；按钮本身仍负责颜色、无障碍语义和点击反馈。 */
internal fun Modifier.globalButtonChrome(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    elevation: Dp = 1.5.dp,
): Modifier = this.shadow(elevation, shape, clip = false)
