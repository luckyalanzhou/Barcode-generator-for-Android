package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 菜单仅保留声明行高，去掉字体首尾额外留白，使上下留白由布局统一控制。 */
internal val MenuLineStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** 图标随系统文字缩放变化，并始终比 16sp 正文换算后的 dp 大 2dp。 */
internal fun menuOptionIconSize(density: Density): Dp = with(density) { 16.sp.toDp() + 2.dp }

/** 勾号以文字绘制，按同一 dp 尺寸规则换算字号。 */
internal fun menuOptionIconFontSize(density: Density): TextUnit = with(density) {
    (16.sp.toDp() + 2.dp).toSp()
}
