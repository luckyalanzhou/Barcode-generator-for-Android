package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.icons.ArrowDownwardIcon
import com.luckyalanzhou.barcodegenerator.icons.ArrowUpwardIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComposeGenerateInputPanel(
    values: List<String>,
    dark: Boolean,
    focusedIndex: Int,
    onValueChange: (Int, String) -> Unit,
    onFocus: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDeleteLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeColors = LocalBarcodeThemeColors.current
    val textColor = themeColors.primary
    val secondary = themeColors.secondary
    val panelColor = themeColors.panel
    val inputColor = themeColors.input
    val panelBorder = themeColors.cardBorder
    val inputBorder = themeColors.inputBorder
    val focusedInputBorder = themeColors.focusedInputBorder

    Surface(
        modifier = modifier.heightIn(min = 72.dp, max = 296.dp),
        shape = RoundedCornerShape(18.dp),
        color = panelColor,
        contentColor = textColor,
        border = BorderStroke(1.dp, panelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            values.forEachIndexed { index, value ->
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(focusedIndex, values.size) {
                    if (focusedIndex == index) {
                        focusRequester.requestFocus()
                    }
                }
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = inputColor,
                        contentColor = textColor,
                        border = BorderStroke(1.dp, if (focusedIndex == index) focusedInputBorder else inputBorder),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                modifier = Modifier.width(32.dp),
                                color = secondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                            BasicTextField(
                                value = value,
                                onValueChange = { onValueChange(index, it) },
                                singleLine = true,
                                textStyle = TextStyle(color = textColor, fontSize = 16.sp, background = Color.Transparent),
                                cursorBrush = SolidColor(textColor),
                                modifier = Modifier.weight(1f).height(48.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { if (it.isFocused) onFocus(index) }
                                    .padding(horizontal = 6.dp, vertical = 13.dp),
                                decorationBox = { field ->
                                    Box {
                                        if (value.isEmpty()) {
                                            Text(
                                                text = "输入一行条码内容",
                                                color = secondary,
                                                fontSize = 16.sp,
                                                style = LocalTextStyle.current.copy(background = Color.Transparent),
                                            )
                                        }
                                        field()
                                    }
                                },
                            )
                        }
                    }
                    if (values.size > 1) {
                        Spacer(Modifier.width(4.dp))
                        GenerateInputAction(ArrowUpwardIcon, "上移", index > 0, 27.dp) { onMoveUp(index) }
                        GenerateInputAction(ArrowDownwardIcon, "下移", index < values.lastIndex, 27.dp) { onMoveDown(index) }
                        GenerateInputAction(DeleteIcon, "删除", true, 24.dp, deleteTint = themeColors.destructive, onLongClick = onDeleteLongClick) { onDelete(index) }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GenerateInputAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    iconSize: androidx.compose.ui.unit.Dp,
    deleteTint: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val themeColors = LocalBarcodeThemeColors.current
    val tint = if (enabled) {
        if (description == "删除") deleteTint ?: themeColors.destructive else themeColors.accent
    } else themeColors.disabled.copy(alpha = 0.42f)
    Surface(
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        ).semantics { role = Role.Button },
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        contentColor = tint,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}
