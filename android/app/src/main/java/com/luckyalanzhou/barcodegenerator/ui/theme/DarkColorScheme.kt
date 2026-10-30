package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme

internal val DarkAppColorScheme = AppColorScheme(
    text = AppTextColors(
        primary = AppColorTokens.Dark.primaryText,
        secondary = AppColorTokens.Dark.secondaryText,
        disabled = AppColorTokens.Dark.disabled,
        destructive = AppColorTokens.Dark.destructive,
        link = AppColorTokens.Dark.link,
        onAccent = AppColorTokens.Dark.onAccent,
    ),
    settingsText = AppSettingsTextColors(
        primary = AppColorTokens.Dark.settingsPrimaryText,
        secondary = AppColorTokens.Dark.secondaryText,
    ),
    surfaces = AppSurfaceColors(
        background = AppColorTokens.Dark.background,
        surface = AppColorTokens.Dark.surface,
        card = AppColorTokens.Dark.card,
        panel = AppColorTokens.Dark.panel,
        input = AppColorTokens.Dark.input,
        inputPanel = AppColorTokens.Dark.inputPanel,
        overlay = AppColorTokens.Dark.overlay,
    ),
    controls = AppControlColors(
        accent = AppColorTokens.Dark.accent,
        primaryAction = AppColorTokens.Dark.primaryAction,
        button = AppColorTokens.Dark.button,
        selectedContainer = AppColorTokens.Dark.selectedContainer,
        disabledContainer = AppColorTokens.Dark.disabledContainer,
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
        focusedInput = AppColorTokens.Dark.accent.copy(alpha = .72f),
        divider = AppColorTokens.Dark.divider,
    ),
    navigation = AppNavigationColors(
        tabUnselected = AppColorTokens.Dark.tabUnselected,
    ),
    content = AppContentColors(
        deleteIcon = AppColorTokens.deleteIcon,
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
        activeTrack = AppColorTokens.Dark.accent,
        inactiveTrack = AppColorTokens.Dark.sliderInactiveTrack,
        thumbBorder = AppColorTokens.Dark.sliderThumbBorder,
        thumb = AppColorTokens.Dark.sliderThumb,
    ),
)

internal fun appDarkMaterialColorScheme(colors: AppColorScheme): ColorScheme = darkColorScheme(
    primary = colors.controls.primaryAction,
    onPrimary = colors.text.onAccent,
    secondary = colors.controls.accent,
    tertiary = colors.controls.accent,
    background = colors.surfaces.background,
    surface = colors.surfaces.surface,
)
