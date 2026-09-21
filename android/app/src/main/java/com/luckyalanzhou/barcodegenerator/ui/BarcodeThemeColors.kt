package com.luckyalanzhou.barcodegenerator.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
internal data class BarcodeTextColors(
    val primary: Color,
    val secondary: Color,
    val disabled: Color,
    val destructive: Color,
    val link: Color,
    val onAccent: Color,
)

@Immutable
internal data class BarcodeSurfaceColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val panel: Color,
    val input: Color,
    val inputPanel: Color,
    val overlay: Color,
)

@Immutable
internal data class BarcodeControlColors(
    val accent: Color,
    val button: Color,
    val progress: Color,
    val progressTrack: Color,
    val thumb: Color,
    val toggleOn: Color,
    val toggleOff: Color,
    val success: Color,
    val progressHighlight: Color,
)

@Immutable
internal data class BarcodeBorderColors(
    val border: Color,
    val button: Color,
    val card: Color,
    val input: Color,
    val focusedInput: Color,
    val divider: Color,
)

@Immutable
internal data class BarcodeNavigationColors(
    val tabUnselected: Color,
    val tabRimTop: Color,
    val tabRimBottom: Color,
    val tabHighlight: Color,
)

@Immutable
internal data class BarcodeContentColors(
    val favoriteActive: Color,
    val icon: Color,
    val folder: Color,
    val childFolder: Color,
    val file: Color,
    val sentContent: Color,
)

@Immutable
internal data class BarcodeCodeColors(
    val qrForeground: Color,
    val qrBackground: Color,
)

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
    val favoriteActive: Color,
    val button: Color,
    val border: Color,
    val buttonBorder: Color,
    val icon: Color,
    val destructive: Color,
    val folder: Color,
    val file: Color,
    val progress: Color,
    val surfaceOverlay: Color,
    val cardBorder: Color,
    val inputBorder: Color,
    val focusedInputBorder: Color,
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

/**
 * 颜色按用途分类的访问入口。
 *
 * 旧的扁平字段暂时保留，保证现有页面和组件可以渐进迁移；新的 UI 代码
 * 应优先使用这些分类入口，后续固定浅色/深色颜色时只需要修改主题文件。
 */
internal val BarcodeThemeColors.text: BarcodeTextColors
    get() = BarcodeTextColors(primary, secondary, disabled, destructive, link, onAccent)

internal val BarcodeThemeColors.surfaces: BarcodeSurfaceColors
    get() = BarcodeSurfaceColors(background, surface, card, panel, input, inputPanel, surfaceOverlay)

internal val BarcodeThemeColors.controls: BarcodeControlColors
    get() = BarcodeControlColors(accent, button, progress, progressTrack, thumb, toggleOn, toggleOff, success, progressHighlight)

internal val BarcodeThemeColors.borders: BarcodeBorderColors
    get() = BarcodeBorderColors(border, buttonBorder, cardBorder, inputBorder, focusedInputBorder, divider)

internal val BarcodeThemeColors.navigation: BarcodeNavigationColors
    get() = BarcodeNavigationColors(tabUnselected, tabRimTop, tabRimBottom, tabHighlight)

internal val BarcodeThemeColors.content: BarcodeContentColors
    get() = BarcodeContentColors(favoriteActive, icon, folder, childFolder, file, sentContent)

internal val BarcodeThemeColors.barcode: BarcodeCodeColors
    get() = BarcodeCodeColors(qrForeground, qrBackground)

internal val LocalBarcodeThemeColors = staticCompositionLocalOf { BarcodeLightThemeColors }

internal fun barcodeThemeColors(dark: Boolean): BarcodeThemeColors =
    if (dark) BarcodeDarkThemeColors else BarcodeLightThemeColors
