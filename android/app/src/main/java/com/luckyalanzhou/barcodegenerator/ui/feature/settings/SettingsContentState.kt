package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import com.luckyalanzhou.barcodegenerator.domain.StyleSettings

/** Display-only inputs projected by the app route from settings presentation state. */
internal data class SettingsContentState(
    val style: StyleSettings,
    val ocrMask: Int,
)
