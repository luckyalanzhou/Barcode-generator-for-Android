package com.luckyalanzhou.barcodegenerator

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val BarcodeLightThemeColors = BarcodeThemeColors(
    background = Color(0xfff2f2f7),
    surface = Color(0xfffbfcff),
    card = Color(0xfff7f9fc),
    input = Color(0xfff4f6fa),
    primary = Color(0xff182230),
    secondary = Color(0xff667085),
    accent = Color(0xff2864d7),
    button = Color(0xffeef3f9),
    border = Color(0xffd9e1ec),
    icon = Color(0xff344054),
    destructive = Color(0xffe58b8b),
    folder = Color(0xff527ca8),
    file = Color(0xff5c8c7b),
    progress = Color(0xff2678db),
    surfaceOverlay = Color.White.copy(alpha = .94f),
    cardBorder = Color(0xffdfe5ed).copy(alpha = .72f),
    inputBorder = Color(0xffe3e8f0),
    focusedInputBorder = Color(0xff7da7d6).copy(alpha = .76f),
    tabSelected = Color(0xff246fc4),
    tabUnselected = Color(0xff64748b),
    tabRimTop = Color.White.copy(alpha = .98f),
    tabRimBottom = Color(0xff6d9fe8).copy(alpha = .58f),
    disabled = Color(0xff99999f),
    progressTrack = Color(0xffe4eaf2),
    childFolder = Color(0xff9b7a57),
    panel = Color.White.copy(alpha = .88f),
    inputPanel = Color(0xfff0f2f5),
    link = Color(0xff2678db),
    qrForeground = Color.Black,
    qrBackground = Color.White,
    divider = Color(0xff667085).copy(alpha = .12f),
    tabHighlight = Color.White.copy(alpha = .62f),
    toggleOn = Color(0xff3478d3),
    toggleOff = Color(0xffd5dbe4),
    thumb = Color.White,
    success = Color(0xff22c55e),
    onAccent = Color.White,
)

/** 浅色主题：颜色只由外观设置选择，不在页面内单独维护主题状态。 */
internal fun barcodeLightColorScheme(background: Color): ColorScheme = lightColorScheme(
    primary = Color(0xff2864d7),
    onPrimary = Color.White,
    secondary = Color(0xff2864d7),
    tertiary = Color(0xff2864d7),
    background = background,
    surface = Color(0xfffbfcff),
)
