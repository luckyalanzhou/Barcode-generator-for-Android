package com.luckyalanzhou.barcodegenerator

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val BarcodeDarkThemeColors = BarcodeThemeColors(
    background = Color.Black,
    surface = Color(0xff1c1c1e),
    card = Color(0xff1b222d),
    input = Color(0xff202c3a),
    primary = Color(0xfff2f4f8),
    secondary = Color(0xffaeb9c9),
    accent = Color(0xffb8ccff),
    button = Color(0xff233246),
    border = Color.White.copy(alpha = 0.10f),
    icon = Color(0xfff2f4f8),
    destructive = Color(0xffffb0b0),
    folder = Color(0xff9bc8f5),
    file = Color(0xff9bd8c0),
    progress = Color(0xff36c8ff),
    surfaceOverlay = Color(0xff252a33).copy(alpha = .98f),
    cardBorder = Color.White.copy(alpha = .10f),
    inputBorder = Color.White.copy(alpha = .12f),
    focusedInputBorder = Color(0xff8dbcf0).copy(alpha = .72f),
    tabSelected = Color(0xfff4f7ff),
    tabUnselected = Color(0xffc4cada),
    tabRimTop = Color(0xfff2f8ff).copy(alpha = .78f),
    tabRimBottom = Color(0xff73baff).copy(alpha = .62f),
    disabled = Color(0xff657388),
    progressTrack = Color(0xff152938),
    childFolder = Color(0xffe0b383),
    panel = Color(0xff182330).copy(alpha = .90f),
    inputPanel = Color(0xff2c2c2e),
    link = Color(0xff8fc1ff),
    qrForeground = Color(0xff111318),
    qrBackground = Color(0xfff1f3f6),
    divider = Color.White.copy(alpha = .14f),
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
    primary = Color(0xffb8ccff),
    onPrimary = Color(0xff10224a),
    secondary = Color(0xffb8ccff),
    tertiary = Color(0xffb8ccff),
    background = background,
    surface = Color(0xff1c1c1e),
)
