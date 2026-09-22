package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

internal data class ResolvedAppAppearance(val isDark: Boolean)

internal val LocalResolvedAppAppearance = staticCompositionLocalOf { ResolvedAppAppearance(false) }

internal fun resolveDarkAppearance(mode: String, systemIsDark: Boolean): Boolean = when (mode) {
    "dark" -> true
    "light" -> false
    else -> systemIsDark
}
