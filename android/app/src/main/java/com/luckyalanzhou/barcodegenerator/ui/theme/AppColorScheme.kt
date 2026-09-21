package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class AppTextColors(
    val primary: Color,
    val secondary: Color,
    val disabled: Color,
    val destructive: Color,
    val link: Color,
    val onAccent: Color,
)

@Immutable
internal data class AppSurfaceColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val panel: Color,
    val input: Color,
    val inputPanel: Color,
    val overlay: Color,
)

@Immutable
internal data class AppControlColors(
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
internal data class AppBorderColors(
    val border: Color,
    val button: Color,
    val card: Color,
    val input: Color,
    val focusedInput: Color,
    val divider: Color,
)

@Immutable
internal data class AppNavigationColors(
    val tabUnselected: Color,
    val tabRimTop: Color,
    val tabRimBottom: Color,
    val tabHighlight: Color,
)

@Immutable
internal data class AppContentColors(
    val favoriteActive: Color,
    val icon: Color,
    val folder: Color,
    val childFolder: Color,
    val file: Color,
    val sentContent: Color,
)

@Immutable
internal data class AppBarcodeColors(
    val qrForeground: Color,
    val qrBackground: Color,
)

@Immutable
internal data class AppThemeMetrics(
    val primaryBorderAlpha: Float,
    val dialogDimAmount: Float,
)

/**
 * 全局 UI 颜色契约。页面只能依赖语义分类，不直接依赖具体色值。
 * 颜色由浅色/深色主题实例提供，外观设置只负责选择当前实例。
 */
@Immutable
internal data class AppColorScheme(
    val text: AppTextColors,
    val surfaces: AppSurfaceColors,
    val controls: AppControlColors,
    val borders: AppBorderColors,
    val navigation: AppNavigationColors,
    val content: AppContentColors,
    val barcode: AppBarcodeColors,
    val metrics: AppThemeMetrics,
)

internal fun appColorScheme(dark: Boolean): AppColorScheme =
    if (dark) DarkAppColorScheme else LightAppColorScheme
