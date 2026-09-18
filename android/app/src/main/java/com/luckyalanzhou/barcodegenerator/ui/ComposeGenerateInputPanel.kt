package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.icons.ArrowCircleDownIcon
import com.luckyalanzhou.barcodegenerator.icons.ArrowCircleUpIcon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
    val textColor = if (dark) Color(0xfff2f4f7) else Color(0xff172033)
    val secondary = if (dark) Color(0xffc5cedb) else Color(0xff667085)
    val panelColor = if (dark) Color(0xff182330).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.88f)
    val inputColor = if (dark) Color(0xff202c3a) else Color(0xfff4f6fa)
    val panelBorder = if (dark) Color.White.copy(alpha = 0.10f) else Color(0xffdfe5ed).copy(alpha = 0.72f)
    val inputBorder = if (dark) Color.White.copy(alpha = 0.12f) else Color(0xffe3e8f0)
    val focusedInputBorder = if (dark) Color(0xff8dbcf0).copy(alpha = 0.72f) else Color(0xff7da7d6).copy(alpha = 0.76f)

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
                        BasicTextField(
                            value = value,
                            onValueChange = { onValueChange(index, it) },
                            singleLine = true,
                            textStyle = TextStyle(color = textColor, fontSize = 16.sp, background = Color.Transparent),
                            cursorBrush = SolidColor(textColor),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                .onFocusChanged { if (it.isFocused) onFocus(index) }
                                .padding(horizontal = 12.dp, vertical = 13.dp),
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
                    if (values.size > 1) {
                        Spacer(Modifier.width(4.dp))
                        GenerateInputAction(ArrowCircleUpIcon, "上移", index > 0, 27.dp) { onMoveUp(index) }
                        GenerateInputAction(ArrowCircleDownIcon, "下移", index < values.lastIndex, 27.dp) { onMoveDown(index) }
                        GenerateInputAction(DeleteIcon, "删除", true, 24.dp, deleteTint = if (dark) Color(0xffffb0b0) else Color(0xffe58b8b), onLongClick = onDeleteLongClick) { onDelete(index) }
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
    deleteTint: Color = Color(0xffe58b8b),
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        if (description == "删除") deleteTint else Color(0xff667085)
    } else {
        Color(0xffb5bdc9)
    }
    Surface(
        modifier = Modifier.size(34.dp).combinedClickable(
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
