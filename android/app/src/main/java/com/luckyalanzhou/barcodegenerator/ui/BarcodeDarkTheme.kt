package com.luckyalanzhou.barcodegenerator.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val BarcodeDarkThemeColors = BarcodeThemeColors(
    background = Color.Black,
    surface = Color(0xff1c1c1e),
    // 所有页面的卡片表面统一接近 Apple 设置页的 secondarySystemGroupedBackground。
    card = Color(0xff1c1c1e),
    input = Color(0xff202c3a),
    // Apple 深色模式设置页的语义文字色：主文字为白色，次文字为 EBEBF5 的 60%。
    primary = Color.White,
    secondary = Color(0xffebebf5).copy(alpha = .60f),
    accent = Color(0xff0a84ff),
    favoriteActive = Color(0xffffbb33),
    button = Color(0xff233246),
    border = Color.White.copy(alpha = 0.10f),
    buttonBorder = Color.White.copy(alpha = 0.08f),
    icon = Color(0xfff2f4f8),
    destructive = Color(0xffffb0b0),
    folder = Color(0xffb3d6f5),
    file = Color(0xff9bd8c0),
    progress = Color(0xff36c8ff),
    surfaceOverlay = Color(0xff1c1c1e).copy(alpha = .98f),
    cardBorder = Color.White.copy(alpha = .10f),
    inputBorder = Color.White.copy(alpha = .12f),
    focusedInputBorder = Color(0xff8dbcf0).copy(alpha = .72f),
    tabUnselected = Color(0xffc4cada),
    tabRimTop = Color(0xfff2f8ff).copy(alpha = .78f),
    tabRimBottom = Color(0xff73baff).copy(alpha = .62f),
    disabled = Color(0xff657388),
    progressTrack = Color(0xff152938),
    childFolder = Color(0xffe0b383),
    panel = Color(0xff1c1c1e),
    inputPanel = Color(0xff2c2c2e),
    link = Color(0xff8fc1ff),
    qrForeground = Color(0xff111318),
    qrBackground = Color(0xfff1f3f6),
    divider = Color.White.copy(alpha = .10f),
    tabHighlight = Color.White.copy(alpha = .24f),
    toggleOn = Color(0xff4f8fe8),
    toggleOff = Color(0xff4a5565),
    thumb = Color.White,
    success = Color(0xff4ade80),
    onAccent = Color(0xff10224a),
    primaryBorderAlpha = .32f,
    dialogDimAmount = .48f,
    progressHighlight = Color.White.copy(alpha = .78f),
    sentContent = Color.White,
)

/** 深色主题：颜色只由外观设置选择，不在页面内单独维护主题状态。 */
internal fun barcodeDarkColorScheme(background: Color): ColorScheme = darkColorScheme(
    primary = BarcodeDarkThemeColors.accent,
    onPrimary = BarcodeDarkThemeColors.onAccent,
    secondary = BarcodeDarkThemeColors.accent,
    tertiary = BarcodeDarkThemeColors.accent,
    background = background,
    surface = BarcodeDarkThemeColors.surface,
)
