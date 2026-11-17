package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.luckyalanzhou.barcodegenerator.ui.theme.MenuMetrics

/** Choice and action menus share the same subtle separator geometry. */
@Composable
internal fun MenuSeparator(color: Color) {
    Box(Modifier.fillMaxWidth().height(MenuMetrics.separatorHeight).background(color))
}
