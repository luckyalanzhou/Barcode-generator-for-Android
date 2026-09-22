package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme

internal val LightAppColorScheme = AppColorScheme(
    text = AppTextColors(
        primary = AppColorTokens.black,
        secondary = AppColorTokens.black,
        disabled = AppColorTokens.Light.disabled,
        destructive = AppColorTokens.Light.destructive,
        link = AppColorTokens.black,
        onAccent = AppColorTokens.white,
    ),
    surfaces = AppSurfaceColors(
        background = AppColorTokens.Light.background,
        surface = AppColorTokens.Light.surface,
        card = AppColorTokens.Light.surface,
        panel = AppColorTokens.Light.surface,
        input = AppColorTokens.Light.input,
        inputPanel = AppColorTokens.Light.inputPanel,
        overlay = AppColorTokens.Light.surface.copy(alpha = .98f),
    ),
    controls = AppControlColors(
        accent = AppColorTokens.Light.accent,
        button = AppColorTokens.Light.button,
        progress = AppColorTokens.Light.progress,
        progressTrack = AppColorTokens.Light.progressTrack,
        thumb = AppColorTokens.white,
        toggleOn = AppColorTokens.Light.toggleOn,
        toggleOff = AppColorTokens.Light.toggleOff,
        success = AppColorTokens.Light.success,
        progressHighlight = AppColorTokens.progressHighlight,
    ),
    borders = AppBorderColors(
        border = AppColorTokens.Light.border,
        button = AppColorTokens.Light.buttonBorder,
        card = AppColorTokens.Light.cardBorder,
        input = AppColorTokens.Light.inputBorder,
        focusedInput = AppColorTokens.Light.focusedInputBorder,
        divider = AppColorTokens.Light.divider,
    ),
    navigation = AppNavigationColors(
        tabUnselected = AppColorTokens.Light.tabUnselected,
        tabRimTop = AppColorTokens.tabRimTopLight,
        tabRimBottom = AppColorTokens.Light.tabRimBottom,
        tabHighlight = AppColorTokens.tabHighlightLight,
    ),
    content = AppContentColors(
        favoriteActive = AppColorTokens.Light.favoriteActive,
        icon = AppColorTokens.black,
        folder = AppColorTokens.black,
        childFolder = AppColorTokens.black,
        file = AppColorTokens.black,
        sentContent = AppColorTokens.white,
    ),
    barcode = AppBarcodeColors(
        qrForeground = AppColorTokens.black,
        qrBackground = AppColorTokens.white,
    ),
)

internal fun appLightMaterialColorScheme(colors: AppColorScheme): ColorScheme = lightColorScheme(
    primary = colors.controls.accent,
    onPrimary = colors.text.onAccent,
    secondary = colors.controls.accent,
    tertiary = colors.controls.accent,
    background = colors.surfaces.background,
    surface = colors.surfaces.surface,
)
