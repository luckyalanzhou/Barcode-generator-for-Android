package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared by Tab and file/folder menus; geometry and gestures remain separate. */
internal object ActionMenuMetrics {
    val width = 200.dp
    val rowHeight = 40.dp
    val titleHeight = 38.dp
    val corner = 12.dp
    val horizontalPadding = 16.dp
    val iconSize = 20.dp
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
        title = if (highContrast) colors.text.primary else colors.text.placeholder,
        separator = colors.text.primary.copy(alpha = if (highContrast) .40f else if (dark) .14f else .12f),
        outline = colors.text.primary.copy(alpha = if (highContrast) .70f else if (dark) .12f else .08f),
        selection = colors.text.primary.copy(alpha = if (highContrast) .20f else if (dark) .12f else .07f),
    )

/** Narrow windows shrink the panel instead of letting it overflow the screen. */
internal fun actionMenuWidthDp(availableWidthDp: Float): Float =
    (availableWidthDp - 24f).coerceIn(1f, ActionMenuMetrics.width.value)
