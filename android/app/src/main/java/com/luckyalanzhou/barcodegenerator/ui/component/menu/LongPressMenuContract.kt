package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** 由 UI 组件提供的菜单项；具体展示方式由应用宿主决定。 */
internal data class TabLongPressAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** 仅供 UI 使用的契约，由菜单锚点和窗口级菜单渲染器共用。 */
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

/** 文件/文件夹菜单与 Tab 菜单共用由应用外壳持有的窗口级宿主。 */
internal val LocalLongPressMenuHost = staticCompositionLocalOf<(TabLongPressMenuState) -> Unit> {
    { error("Long-press menu requires ComposeAppShell") }
}
