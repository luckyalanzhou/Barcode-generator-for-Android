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
        val childFolder = Color(0xff6883A5)
        // 普通按钮不添加灰色填充，直接透出所在页面或卡片；边框与文字保留辨识度。
        val button = Color.Transparent
        val selectedContainer = Color(0xffe8f2ff)
        val disabledContainer = Color.Transparent
        val border = Color(0xffd9e1ec)
        val buttonBorder = Color.Black.copy(alpha = .22f)
        val destructive = Color(0xffc2413b)
        // Apple system blue is shared by the primary action and progress fill.
        val cardBorder = border
        val inputBorder = Color(0xffe3e8f0)
        val tabUnselected = Color(0xff64748b)
        val disabled = Color(0xff7d8795)
        val placeholder = Color(0xff3c3c43).copy(alpha = .60f)
        val progressTrack = Color(0xffe4eaf2)
        val inputPanel = Color(0xfff0f2f5)
        val divider = Color(0xff667085).copy(alpha = .12f)
        val toggleOn = Color(0xff34c759)
        val toggleOff = Color(0xffd5dbe4)
        val success = Color(0xff22c55e)
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
        // 深色模式同样不人为叠加灰色按钮底色，禁用状态由文字和交互表达。
        val button = Color.Transparent
        val selectedContainer = Color(0xff17334d)
        val disabledContainer = Color.Transparent
        val border = Color.White.copy(alpha = 0.10f)
        val buttonBorder = Color.White.copy(alpha = .26f)
        val destructive = Color(0xffffb0b0)
        // Dark-mode system blue keeps primary actions consistent with tabs and sliders.
        val cardBorder = Color.White.copy(alpha = 0.055f)
        val inputBorder = Color.White.copy(alpha = 0.14f)
        val tabUnselected = Color(0xffc4cada)
        val disabled = Color(0xff657388)
        val progressTrack = Color(0xff152938)
        val inputPanel = Color(0xff2c2c2e)
        val secondaryText = Color(0xff8e8e93)
        val placeholder = Color(0xffebebf5).copy(alpha = .60f)
        val onAccent = Color(0xff10224a)
        val divider = Color.White.copy(alpha = .10f)
        val toggleOn = Color(0xff30d158)
        val toggleOff = Color(0xff4a5565)
        val success = Color(0xff4ade80)
        val sliderInactiveTrack = Color(0xff787880).copy(alpha = .34f)
        val sliderThumbBorder = Color.White.copy(alpha = .18f)
        val sliderThumb = Color(0xfff8f8f8)
        val settingsPrimaryText = Color(0xffffffff)
        val settingsSecondaryText = Color(0xffebebf5).copy(alpha = .60f)
        val folder = Color(0xff8abcf5)
        val childFolder = Color(0xffA1B6CF)
        val file = Color(0xff9bd8c0)
        val icon = Color(0xfff2f4f8)
        val qrForeground = Color(0xff111318)
        val qrBackground = Color(0xfff1f3f6)
    }

    internal val black = Color.Black
    // Soft red shared by every delete glyph; destructive text/buttons keep their own token.
    internal val deleteIcon = Color(0xffdb6d6d)
    internal val white = Color.White
    internal val progressHighlight = Color.White.copy(alpha = .78f)
}
