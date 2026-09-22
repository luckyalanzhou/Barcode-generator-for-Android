package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.ui.graphics.Color

/** 原始设计令牌。页面和组件不得直接引用这些值，应使用 AppColorScheme 的语义颜色。 */
internal object AppColorTokens {
    internal object Light {
        val background = Color(0xfff2f2f7)
        val surface = Color.White
        val input = Color(0xfff4f6fa)
        val accent = Color(0xff007aff)
        val favoriteActive = Color(0xffd97706)
        val button = Color(0xffeef3f9)
        val border = Color(0xffd9e1ec)
        val buttonBorder = Color(0xffcbd6e4).copy(alpha = .72f)
        val destructive = Color(0xffc2413b)
        val progress = Color(0xff2166d1)
        val cardBorder = border
        val inputBorder = Color(0xffe3e8f0)
        val focusedInputBorder = Color(0xff7da7d6).copy(alpha = .76f)
        val tabUnselected = Color(0xff64748b)
        val tabRimBottom = Color(0xff6d9fe8).copy(alpha = .58f)
        val disabled = Color(0xff7d8795)
        val placeholder = Color(0xff8e8e93)
        val progressTrack = Color(0xffe4eaf2)
        val inputPanel = Color(0xfff0f2f5)
        val divider = Color(0xff667085).copy(alpha = .12f)
        val toggleOn = Color(0xff2868c7)
        val toggleOff = Color(0xffd5dbe4)
        val success = Color(0xff22c55e)
    }

    internal object Dark {
        val background = Color.Black
        val surface = Color(0xff1c1c1e)
        val input = Color(0xff202c3a)
        val accent = Color(0xff0a84ff)
        val favoriteActive = Color(0xffffbb33)
        val button = Color(0xff233246)
        val border = Color.White.copy(alpha = 0.10f)
        val buttonBorder = Color.White.copy(alpha = 0.08f)
        val destructive = Color(0xffffb0b0)
        val progress = Color(0xff36c8ff)
        val cardBorder = Color.White.copy(alpha = 0.10f)
        val inputBorder = Color.White.copy(alpha = 0.22f)
        val focusedInputBorder = Color(0xff8dbcf0).copy(alpha = .72f)
        val tabUnselected = Color(0xffc4cada)
        val tabRimBottom = Color(0xff73baff).copy(alpha = .62f)
        val disabled = Color(0xff657388)
        val progressTrack = Color(0xff152938)
        val inputPanel = Color(0xff2c2c2e)
        val link = Color(0xff8fc1ff)
        val secondaryText = Color(0xff8e8e93)
        val placeholder = Color(0xff8e8e93)
        val onAccent = Color(0xff10224a)
        val divider = Color.White.copy(alpha = .10f)
        val toggleOn = Color(0xff4f8fe8)
        val toggleOff = Color(0xff4a5565)
        val success = Color(0xff4ade80)
        val folder = Color(0xffb3d6f5)
        val childFolder = Color(0xffe0b383)
        val file = Color(0xff9bd8c0)
        val icon = Color(0xfff2f4f8)
        val qrForeground = Color(0xff111318)
        val qrBackground = Color(0xfff1f3f6)
    }

    internal val black = Color.Black
    internal val white = Color.White
    internal val tabRimTopLight = Color.White.copy(alpha = .98f)
    internal val tabRimTopDark = Color(0xfff2f8ff).copy(alpha = .78f)
    internal val tabHighlightLight = Color.White.copy(alpha = .62f)
    internal val tabHighlightDark = Color.White.copy(alpha = .24f)
    internal val progressHighlight = Color.White.copy(alpha = .78f)
}
