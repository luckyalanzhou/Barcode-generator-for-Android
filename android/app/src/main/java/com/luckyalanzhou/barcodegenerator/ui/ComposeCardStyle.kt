package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.MainActivity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

/** 全局卡片基座：统一圆角、边缘分离度和轻量阴影，页面保留自己的表面颜色。 */
@Composable
internal fun Modifier.globalCardSurface(
    dark: Boolean,
    color: Color,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    elevation: Dp = 3.dp,
): Modifier = this
    .shadow(minOf(elevation, 2.dp), shape, clip = true)
    .clip(shape)
    .background(color)
    .border(
        1.dp,
        LocalBarcodeThemeColors.current.cardBorder,
        shape,
    )

/** 按钮统一的轻量边缘与浮起效果；按钮本身仍负责颜色、语义和点击反馈。 */
@Composable
internal fun Modifier.globalButtonChrome(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    elevation: Dp = 1.5.dp,
    borderColor: Color? = null,
): Modifier {
    val themeColors = LocalBarcodeThemeColors.current
    return this
        .shadow(minOf(elevation, 0.5.dp), shape, clip = false)
        .border(0.5.dp, borderColor ?: themeColors.buttonBorder, shape)
}
