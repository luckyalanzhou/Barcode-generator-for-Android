package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** A menu item contributed by a UI component; the application host decides how it is presented. */
internal data class TabLongPressAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** UI-only contract shared by menu anchors and the window-level menu renderer. */
internal data class TabLongPressMenuState(
    val anchorBoundsOnScreen: Rect,
    val focusIcon: ImageVector,
    val focusLabel: String,
    val focusTint: Color,
    val dark: Boolean,
    val actions: List<TabLongPressAction>,
    val restoreFocus: () -> Unit = {},
    val title: String = "操作",
    val tabAnchor: Boolean = true,
    val menuAnchorBoundsOnScreen: Rect = anchorBoundsOnScreen,
    // 来源行与浮层共用内容组件，避免长按后丢失数量、时间、箭头或改变字号。
    val sourceContent: (@Composable () -> Unit)? = null,
)

/** File/folder and Tab menus share the window-level host owned by the application shell. */
internal val LocalLongPressMenuHost = staticCompositionLocalOf<(TabLongPressMenuState) -> Unit> {
    { error("Long-press menu requires ComposeAppShell") }
}
