package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Tab 菜单与文件/文件夹菜单共用此样式；各自的布局和手势保持独立。 */
internal object ActionMenuMetrics {
    val width = 200.dp
    val corner = 12.dp
    val horizontalPadding = 16.dp
    val iconGap = 12.dp
    val separatorHeight = .5.dp
}

@Immutable
internal data class ActionMenuColors(
    val title: Color,
    val separator: Color,
    val outline: Color,
    val selection: Color,
)

internal fun actionMenuColors(colors: AppColorScheme, dark: Boolean, highContrast: Boolean): ActionMenuColors =
    ActionMenuColors(
        title = if (highContrast) colors.text.primary else colors.text.secondary,
        separator = colors.text.primary.copy(alpha = if (highContrast) .40f else if (dark) .14f else .12f),
        outline = colors.text.primary.copy(alpha = if (highContrast) .70f else if (dark) .12f else .08f),
        selection = colors.text.primary.copy(alpha = if (highContrast) .20f else if (dark) .12f else .07f),
    )

/** 窄窗口会缩小菜单面板，避免内容超出屏幕。 */
internal fun actionMenuWidthDp(availableWidthDp: Float): Float =
    (availableWidthDp - 24f).coerceIn(1f, ActionMenuMetrics.width.value)
