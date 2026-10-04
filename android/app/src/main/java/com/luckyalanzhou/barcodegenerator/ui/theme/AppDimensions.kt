package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 与颜色无关的全局尺寸令牌，主题切换不改变布局尺寸。 */
@Immutable
internal data class AppDimensions(
    val pageHorizontalPadding: Dp = 18.dp,
    val pageTopPadding: Dp = 18.dp,
    val pageBottomPadding: Dp = 10.dp,
    val pageHeaderHeight: Dp = 52.dp,
    val settingsHeaderHeight: Dp = 44.dp,
    val bottomTabBarHeight: Dp = 72.dp,
    val cardCornerRadius: Dp = 18.dp,
    val buttonCornerRadius: Dp = 16.dp,
    val settingsRowHeight: Dp = 48.dp,
    val settingsCardSpacing: Dp = 8.dp,
)

internal val LocalAppDimensions = staticCompositionLocalOf { AppDimensions() }

/** Preserve the normal 72dp bar while reserving extra space for scaled label line height. */
internal fun bottomTabHeightForLabel(labelHeightDp: Float): Float =
    72f + ((if (labelHeightDp.isFinite()) labelHeightDp else 24f) - 24f).coerceAtLeast(0f)
