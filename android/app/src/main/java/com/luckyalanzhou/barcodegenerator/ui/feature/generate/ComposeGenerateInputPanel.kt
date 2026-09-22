package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.theme.*

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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
    val themeColors = LocalAppColorScheme.current
    val textColor = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val inputColor = themeColors.surfaces.input
    val inputBorder = themeColors.borders.input
    val focusedInputBorder = themeColors.borders.focusedInput

    Column(
        modifier = modifier
            .heightIn(min = 72.dp, max = 296.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            // Keep the complete input + reorder/delete action row aligned to the
            // full-width action row below (Add row / Capture text).
            .padding(vertical = 10.dp),
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
                                color = textColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            BasicTextField(
                                value = value,
                                onValueChange = { onValueChange(index, it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = textColor,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    background = Color.Transparent,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                                ),
                                cursorBrush = SolidColor(textColor),
                                modifier = Modifier.weight(1f).height(48.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { if (it.isFocused) onFocus(index) }
                                    .padding(horizontal = 6.dp),
                                decorationBox = { field ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (value.isEmpty()) {
                                            Text(
                                                text = "输入一行条码内容",
                                                style = LocalTextStyle.current.copy(
                                                    color = themeColors.text.placeholder,
                                                    fontSize = 14.sp,
                                                    lineHeight = 20.sp,
                                                    background = Color.Transparent,
                                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                    lineHeightStyle = LineHeightStyle(
                                                        alignment = LineHeightStyle.Alignment.Center,
                                                        trim = LineHeightStyle.Trim.Both,
                                                    ),
                                                ),
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
                        GenerateInputAction(DeleteIcon, "删除", true, 24.dp, deleteTint = themeColors.text.destructive, onLongClick = onDeleteLongClick) { onDelete(index) }
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
    val themeColors = LocalAppColorScheme.current
    val tint = if (enabled) {
        if (description == "删除") deleteTint ?: themeColors.text.destructive else themeColors.controls.accent
    } else themeColors.text.disabled.copy(alpha = 0.42f)
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
