package com.luckyalanzhou.barcodegenerator.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val BarcodeLightThemeColors = BarcodeThemeColors(
    background = Color(0xfff2f2f7),
    // Apple 浅色模式设置页：分组背景为 F2F2F7，卡片表面为纯白。
    surface = Color.White,
    card = Color.White,
    input = Color(0xfff4f6fa),
    primary = Color.Black,
    // 浅色模式正文、辅助文字和占位文字统一使用黑色；危险色单独保留。
    secondary = Color.Black,
    accent = Color(0xff007aff),
    favoriteActive = Color(0xffd97706),
    button = Color(0xffeef3f9),
    border = Color(0xffd9e1ec),
    buttonBorder = Color(0xffcbd6e4).copy(alpha = .72f),
    icon = Color.Black,
    destructive = Color(0xffc2413b),
    folder = Color.Black,
    file = Color.Black,
    progress = Color(0xff2166d1),
    surfaceOverlay = Color.White.copy(alpha = .98f),
    cardBorder = Color(0xffdfe5ed).copy(alpha = .72f),
    inputBorder = Color(0xffe3e8f0),
    focusedInputBorder = Color(0xff7da7d6).copy(alpha = .76f),
    // Tab 图标和文字维持原有的非选中态颜色，不参与全局文字黑色调整。
    tabUnselected = Color(0xff64748b),
    tabRimTop = Color.White.copy(alpha = .98f),
    tabRimBottom = Color(0xff6d9fe8).copy(alpha = .58f),
    disabled = Color(0xff7d8795),
    progressTrack = Color(0xffe4eaf2),
    childFolder = Color.Black,
    panel = Color.White,
    inputPanel = Color(0xfff0f2f5),
    link = Color.Black,
    qrForeground = Color.Black,
    qrBackground = Color.White,
    divider = Color(0xff667085).copy(alpha = .12f),
    tabHighlight = Color.White.copy(alpha = .62f),
    toggleOn = Color(0xff2868c7),
    toggleOff = Color(0xffd5dbe4),
    thumb = Color.White,
    success = Color(0xff22c55e),
    onAccent = Color.White,
    primaryBorderAlpha = .22f,
    dialogDimAmount = .34f,
    progressHighlight = Color.White.copy(alpha = .78f),
    sentContent = Color.White,
)

/** 浅色主题：颜色只由外观设置选择，不在页面内单独维护主题状态。 */
internal fun barcodeLightColorScheme(background: Color): ColorScheme = lightColorScheme(
    primary = BarcodeLightThemeColors.accent,
    onPrimary = BarcodeLightThemeColors.onAccent,
    secondary = BarcodeLightThemeColors.accent,
    tertiary = BarcodeLightThemeColors.accent,
    background = background,
    surface = BarcodeLightThemeColors.surface,
)
