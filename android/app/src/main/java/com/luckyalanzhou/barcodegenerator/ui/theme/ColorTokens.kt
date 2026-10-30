package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.ui.graphics.Color

/** 原始设计令牌。页面和组件不得直接引用这些值，应使用 AppColorScheme 的语义颜色。 */
internal object AppColorTokens {
    internal object Light {
        // 冷调浅灰画布与白色内容面分层，减少纯白铺满屏幕的刺眼感。
        val background = Color(0xfff3f5f8)
        val surface = Color.White
        val input = Color(0xfff5f7fa)
        val inputPanel = Color(0xffeef2f7)
        val primaryText = Color(0xff171b22)
        // 统一操作蓝，并满足浅色表面的普通文字对比度。
        val accent = Color(0xff0066cc)
        val primaryAction = Color(0xff0066cc)
        val link = Color(0xff0066cc)
        val favoriteActive = Color(0xffd97706)
        val folder = Color(0xff527fc8)
        val childFolder = Color(0xff667f9f)
        // 普通按钮不添加灰色填充，直接透出所在页面或卡片；边框与文字保留辨识度。
        val button = Color.Transparent
        val selectedContainer = Color(0xffe8f2ff)
        val disabledContainer = Color.Transparent
        val border = Color(0xffd8e0e9)
        val buttonBorder = Color.Black.copy(alpha = .22f)
        val destructive = Color(0xffc2413b)
        // 强调蓝继续用于图标和进度；小字号文字与填充主操作使用独立的高对比蓝色。
        val cardBorder = border
        val inputBorder = Color(0xffdfe5ed)
        val tabUnselected = Color(0xff667388)
        val disabled = Color(0xff7d8795)
        val secondaryText = Color(0xff596474)
        val progressTrack = Color(0xffe1e8f0)
        val divider = Color(0xff667085).copy(alpha = .12f)
        val toggleOn = Color(0xff249b53)
        val toggleOff = Color(0xffd2dae4)
        val success = Color(0xff218a4b)
        val sliderInactiveTrack = Color(0xff787878).copy(alpha = .20f)
        val sliderThumbBorder = Color(0xffc7c7cc).copy(alpha = .30f)
        val sliderThumb = Color.White
        val settingsPrimaryText = primaryText
    }

    internal object Dark {
        // 纯黑作为底层画布，卡片、面板和输入区逐层提亮，接近 iOS 深色模式的表面层级。
        val background = Color(0xff000000)
        val surface = Color(0xff1c1c1e)
        val card = surface
        val panel = Color(0xff232326)
        val input = Color(0xff2c2c2e)
        val inputPanel = surface
        val overlay = Color(0xff2c2c2e)
        val primaryText = Color(0xfff2f4f8)
        val accent = Color(0xff66a9ff)
        val primaryAction = Color(0xff2673c7)
        val link = Color(0xff8abcf5)
        val favoriteActive = Color(0xffffbf55)
        // 深色模式同样不人为叠加灰色按钮底色，禁用状态由文字和交互表达。
        val button = Color.Transparent
        val selectedContainer = Color(0xff203953)
        val disabledContainer = Color.Transparent
        val border = Color(0xff38383a)
        val buttonBorder = Color.White.copy(alpha = .26f)
        val destructive = Color(0xffffb0b0)
        // 深色强调蓝用于图标与进度；文字链接和填充主操作采用各自的高对比色。
        val cardBorder = Color(0xff2c2c2e)
        val inputBorder = Color(0xff48484a)
        val tabUnselected = Color(0xffaeb8c6)
        val disabled = Color(0xff788393)
        val progressTrack = Color(0xff2c2c2e)
        val secondaryText = Color(0xffb1bdca)
        val onAccent = Color.White
        val divider = Color.White.copy(alpha = .12f)
        val toggleOn = Color(0xff49c875)
        val toggleOff = Color(0xff485361)
        val success = Color(0xff55c878)
        val sliderInactiveTrack = Color(0xff787880).copy(alpha = .34f)
        val sliderThumbBorder = Color.White.copy(alpha = .18f)
        val sliderThumb = Color(0xfff8f8f8)
        val settingsPrimaryText = primaryText
        val folder = Color(0xff8abcf5)
        val childFolder = Color(0xffA1B6CF)
        val file = Color(0xff9bd8c0)
        val icon = primaryText
        val qrForeground = Color(0xff111318)
        val qrBackground = Color(0xfff1f3f6)
    }

    internal val black = Color.Black
// 所有删除图标共用柔和红色；破坏性操作的文字和按钮仍使用各自的颜色 token。
    internal val deleteIcon = Color(0xffdb6d6d)
    internal val white = Color.White
    internal val progressHighlight = Color.White.copy(alpha = .78f)
}
