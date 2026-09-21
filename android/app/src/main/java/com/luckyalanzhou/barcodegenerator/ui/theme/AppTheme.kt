package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** 应用主题注入入口。窗口系统栏同步仍由 Activity 负责，避免弹窗改变宿主窗口。 */
@Composable
internal fun AppTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = appColorScheme(dark)
    val materialColors = if (dark) appDarkMaterialColorScheme(colors) else appLightMaterialColorScheme(colors)
    androidx.compose.runtime.CompositionLocalProvider(LocalAppColorScheme provides colors) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}
