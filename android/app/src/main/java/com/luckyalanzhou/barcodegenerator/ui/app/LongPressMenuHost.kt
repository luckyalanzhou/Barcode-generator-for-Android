package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.runtime.staticCompositionLocalOf

/** File/folder and Tab menus are rendered by the same window-level host. */
internal val LocalLongPressMenuHost = staticCompositionLocalOf<(TabLongPressMenuState) -> Unit> {
    { error("Long-press menu requires ComposeAppShell") }
}
