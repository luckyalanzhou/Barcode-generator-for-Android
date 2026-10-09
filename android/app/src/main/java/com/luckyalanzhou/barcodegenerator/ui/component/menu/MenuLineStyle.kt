package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

/** 菜单仅保留声明行高，去掉字体首尾额外留白，使上下 6dp 由布局统一控制。 */
internal val MenuLineStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)
