package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme

internal val DarkAppColorScheme = AppColorScheme(
    text = AppTextColors(
        primary = AppColorTokens.white,
        secondary = AppColorTokens.Dark.secondaryText,
        placeholder = AppColorTokens.Dark.placeholder,
        disabled = AppColorTokens.Dark.disabled,
        destructive = AppColorTokens.Dark.destructive,
        link = AppColorTokens.Dark.link,
        onAccent = AppColorTokens.Dark.onAccent,
    ),
    surfaces = AppSurfaceColors(
        background = AppColorTokens.Dark.background,
        surface = AppColorTokens.Dark.surface,
        card = AppColorTokens.Dark.surface,
        panel = AppColorTokens.Dark.surface,
        input = AppColorTokens.Dark.input,
        inputPanel = AppColorTokens.Dark.inputPanel,
        overlay = AppColorTokens.Dark.surface.copy(alpha = .98f),
    ),
    controls = AppControlColors(
        accent = AppColorTokens.Dark.accent,
        button = AppColorTokens.Dark.button,
        progress = AppColorTokens.Dark.progress,
        progressTrack = AppColorTokens.Dark.progressTrack,
        thumb = AppColorTokens.white,
        toggleOn = AppColorTokens.Dark.toggleOn,
        toggleOff = AppColorTokens.Dark.toggleOff,
        success = AppColorTokens.Dark.success,
        progressHighlight = AppColorTokens.progressHighlight,
    ),
    borders = AppBorderColors(
        border = AppColorTokens.Dark.border,
        button = AppColorTokens.Dark.buttonBorder,
        card = AppColorTokens.Dark.cardBorder,
        input = AppColorTokens.Dark.inputBorder,
        focusedInput = AppColorTokens.Dark.focusedInputBorder,
        divider = AppColorTokens.Dark.divider,
    ),
    navigation = AppNavigationColors(
        tabUnselected = AppColorTokens.Dark.tabUnselected,
        tabRimTop = AppColorTokens.tabRimTopDark,
        tabRimBottom = AppColorTokens.Dark.tabRimBottom,
        tabHighlight = AppColorTokens.tabHighlightDark,
    ),
    content = AppContentColors(
        favoriteActive = AppColorTokens.Dark.favoriteActive,
        icon = AppColorTokens.Dark.icon,
        folder = AppColorTokens.Dark.folder,
        childFolder = AppColorTokens.Dark.childFolder,
        file = AppColorTokens.Dark.file,
        sentContent = AppColorTokens.white,
    ),
    barcode = AppBarcodeColors(
        qrForeground = AppColorTokens.Dark.qrForeground,
        qrBackground = AppColorTokens.Dark.qrBackground,
    ),
    sliders = AppSliderColors(
        activeTrack = AppColorTokens.Dark.sliderActiveTrack,
        inactiveTrack = AppColorTokens.Dark.sliderInactiveTrack,
        thumb = AppColorTokens.Dark.sliderThumb,
    ),
)

internal fun appDarkMaterialColorScheme(colors: AppColorScheme): ColorScheme = darkColorScheme(
    primary = colors.controls.accent,
    onPrimary = colors.text.onAccent,
    secondary = colors.controls.accent,
    tertiary = colors.controls.accent,
    background = colors.surfaces.background,
    surface = colors.surfaces.surface,
)
