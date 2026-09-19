package com.luckyalanzhou.barcodegenerator

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 全局 UI 颜色契约；页面只读取这里的语义颜色，不自行判断深浅模式。 */
@Immutable
internal data class BarcodeThemeColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val input: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val button: Color,
    val border: Color,
    val icon: Color,
    val destructive: Color,
    val folder: Color,
    val file: Color,
    val progress: Color,
    val surfaceOverlay: Color,
    val cardBorder: Color,
    val inputBorder: Color,
    val focusedInputBorder: Color,
    val tabSelected: Color,
    val tabUnselected: Color,
    val tabRimTop: Color,
    val tabRimBottom: Color,
    val disabled: Color,
    val progressTrack: Color,
    val childFolder: Color,
    val panel: Color,
    val inputPanel: Color,
    val link: Color,
    val qrForeground: Color,
    val qrBackground: Color,
    val divider: Color,
    val tabHighlight: Color,
    val toggleOn: Color,
    val toggleOff: Color,
    val thumb: Color,
    val success: Color,
    val onAccent: Color,
    val primaryBorderAlpha: Float,
    val dialogDimAmount: Float,
    val progressHighlight: Color,
    val sentContent: Color,
)

internal val LocalBarcodeThemeColors = staticCompositionLocalOf { BarcodeLightThemeColors }

internal fun barcodeThemeColors(dark: Boolean): BarcodeThemeColors =
    if (dark) BarcodeDarkThemeColors else BarcodeLightThemeColors
