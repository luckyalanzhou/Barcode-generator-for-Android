package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.theme.MenuMetrics

/** 菜单仅保留声明行高，去掉字体首尾额外留白，使上下留白由布局统一控制。 */
internal val MenuLineStyle = TextStyle(
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** 下拉菜单和长按上下文菜单共用的选项排版规格。 */
internal val menuOptionLineHeight: TextUnit = MenuMetrics.optionLineHeight
internal val menuOptionVerticalPadding = MenuMetrics.optionVerticalPadding

/** 图标随系统文字缩放变化，并始终比 16sp 正文换算后的 dp 大 4dp。 */
internal fun menuOptionIconSize(density: Density): Dp = with(density) { MenuMetrics.optionTextSize.toDp() + 4.dp }

