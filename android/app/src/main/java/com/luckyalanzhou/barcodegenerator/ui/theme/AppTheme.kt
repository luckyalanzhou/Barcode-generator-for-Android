package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme

/** 应用主题注入入口。窗口系统栏同步仍由 Activity 负责，避免弹窗改变宿主窗口。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppTheme(
    appearanceMode: String,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkAppearance(appearanceMode, isSystemInDarkTheme())
    val colors = appColorScheme(dark)
    val materialColors = if (dark) appDarkMaterialColorScheme(colors) else appLightMaterialColorScheme(colors)
    CompositionLocalProvider(
        LocalResolvedAppAppearance provides ResolvedAppAppearance(dark),
        LocalAppColorScheme provides colors,
        LocalAppDimensions provides AppDimensions(),
        LocalRippleConfiguration provides null,
    ) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}
