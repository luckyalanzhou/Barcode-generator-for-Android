package com.luckyalanzhou.barcodegenerator.ui.component

import androidx.compose.runtime.Composable
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuChoiceItem

/** 单选菜单复用自然行高与前置勾号，不引入 Material 默认最小高度。 */
@Composable
internal fun SingleChoiceMenuItem(label: String, isSelected: Boolean, highlightSelection: Boolean = true, onClick: () -> Unit) {
    MenuChoiceItem(label, isSelected, highlightSelection = highlightSelection, onClick = onClick)
}
