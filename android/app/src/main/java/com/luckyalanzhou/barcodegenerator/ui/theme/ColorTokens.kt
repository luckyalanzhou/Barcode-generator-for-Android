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
        val folder = Color(0xff5b8def)
        val childFolder = Color(0xff83aeea)
        // Low-saturation blue surface for secondary actions; primary actions
        // use the system accent below so the hierarchy remains clear.
        val button = Color(0xffeaf2ff)
        val border = Color(0xffd9e1ec)
        val buttonBorder = Color(0xffd6e2f2)
        val destructive = Color(0xffc2413b)
        // Apple system blue is shared by the primary action and progress fill.
        val progress = Color(0xff007aff)
        val cardBorder = border
        val inputBorder = Color(0xffe3e8f0)
        val focusedInputBorder = Color(0xff7da7d6).copy(alpha = .76f)
        val tabUnselected = Color(0xff64748b)
        val tabRimBottom = Color(0xff6d9fe8).copy(alpha = .58f)
        val disabled = Color(0xff7d8795)
        val placeholder = Color(0xff3c3c43).copy(alpha = .60f)
        val progressTrack = Color(0xffe4eaf2)
        val inputPanel = Color(0xfff0f2f5)
        val divider = Color(0xff667085).copy(alpha = .12f)
        val toggleOn = Color(0xff34c759)
        val toggleOff = Color(0xffd5dbe4)
        val success = Color(0xff22c55e)
        val sliderActiveTrack = Color(0xff007aff)
        val sliderInactiveTrack = Color(0xff787878).copy(alpha = .20f)
        val sliderThumbBorder = Color(0xffc7c7cc).copy(alpha = .30f)
        val sliderThumb = Color.White
        val settingsPrimaryText = Color(0xff000000)
        val settingsSecondaryText = Color(0xff3c3c43).copy(alpha = .60f)
    }

    internal object Dark {
        val background = Color.Black
        val surface = Color(0xff1c1c1e)
        val input = Color(0xff202c3a)
        val accent = Color(0xff0a84ff)
        val favoriteActive = Color(0xffffbb33)
        val button = Color(0xff1c2a3a)
        val border = Color.White.copy(alpha = 0.10f)
        val buttonBorder = Color.White.copy(alpha = 0.08f)
        val destructive = Color(0xffffb0b0)
        // Dark-mode system blue keeps primary actions consistent with tabs and sliders.
        val progress = Color(0xff0a84ff)
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
        val placeholder = Color(0xffebebf5).copy(alpha = .60f)
        val onAccent = Color(0xff10224a)
        val divider = Color.White.copy(alpha = .10f)
        val toggleOn = Color(0xff30d158)
        val toggleOff = Color(0xff4a5565)
        val success = Color(0xff4ade80)
        val sliderActiveTrack = Color(0xff0a84ff)
        val sliderInactiveTrack = Color(0xff787880).copy(alpha = .34f)
        val sliderThumbBorder = Color.White.copy(alpha = .18f)
        val sliderThumb = Color(0xfff8f8f8)
        val settingsPrimaryText = Color(0xffffffff)
        val settingsSecondaryText = Color(0xffebebf5).copy(alpha = .60f)
        val folder = Color(0xff8abcf5)
        val childFolder = Color(0xffb8d4f5)
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
