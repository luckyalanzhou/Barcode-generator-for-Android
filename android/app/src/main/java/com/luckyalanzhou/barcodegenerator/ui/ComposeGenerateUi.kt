package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.icons.AddIcon
import com.luckyalanzhou.barcodegenerator.icons.ArrowCircleDownIcon
import com.luckyalanzhou.barcodegenerator.icons.ArrowCircleUpIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.PhotoCameraIcon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComposeGeneratePage(
    viewModel: BarcodeViewModel,
    initialFormat: String,
    dark: Boolean,
    onCaptureText: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val editorState by viewModel.generateEditorState.collectAsStateWithLifecycle()
    val values = remember {
        mutableStateListOf<String>().apply {
            addAll(editorState.inputDraft.ifEmpty { listOf("") })
        }
    }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var formatName by remember { mutableStateOf(initialFormat) }
    var formatExpanded by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var formatButtonWidth by remember { mutableIntStateOf(0) }
    val textColor = if (dark) Color(0xfff2f4f7) else Color(0xff172033)
    val secondary = if (dark) Color(0xffc5cedb) else Color(0xff667085)
    val cardColor = if (dark) Color(0xff182330).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.88f)
    val inputColor = if (dark) Color(0xff202c3a) else Color(0xfff4f6fa)
    val cardBorder = if (dark) Color.White.copy(alpha = 0.10f) else Color(0xffdfe5ed).copy(alpha = 0.72f)
    val inputBorder = if (dark) Color.White.copy(alpha = 0.12f) else Color(0xffe3e8f0)
    val focusedInputBorder = if (dark) Color(0xff8dbcf0).copy(alpha = 0.72f) else Color(0xff7da7d6).copy(alpha = 0.76f)
    val density = LocalDensity.current
    val formatAnchorWidth = formatButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }

    fun syncDraft() { viewModel.updateInputDraft(values) }

    LaunchedEffect(editorState.inputDraft) {
        if (editorState.inputDraft.isNotEmpty() && values.toList() != editorState.inputDraft) {
            values.clear()
            values.addAll(editorState.inputDraft)
            focusedIndex = -1
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BarcodeEvent.RecognizedText -> viewModel.updateInputDraft(event.lines)
                is BarcodeEvent.Notice -> onNotice(event.message)
            }
        }
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("\u6e05\u7a7a\u6240\u6709\u8f93\u5165\uff1f") },
            text = { Text("\u5c06\u5220\u9664\u5f53\u524d\u6240\u6709\u8f93\u5165\u5185\u5bb9\uff0c\u5e76\u4fdd\u7559\u4e00\u4e2a\u7a7a\u767d\u8f93\u5165\u6846\u3002") },
            confirmButton = { Button(onClick = { values.clear(); values.add(""); syncDraft(); clearDialog = false }) { Text("\u6e05\u7a7a") } },
            dismissButton = { OutlinedButton(onClick = { clearDialog = false }) { Text("\u53d6\u6d88") } }
        )
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 296.dp)
                .shadow(8.dp, RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(cardColor)
                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEachIndexed { index, value ->
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = value,
                            onValueChange = { values[index] = it; syncDraft() },
                            singleLine = true,
                            textStyle = TextStyle(color = textColor, fontSize = 16.sp, background = Color.Transparent),
                            cursorBrush = SolidColor(textColor),
                            modifier = Modifier.weight(1f).height(48.dp)
                                .shadow(1.dp, RoundedCornerShape(14.dp), clip = false)
                                .clip(RoundedCornerShape(14.dp))
                                .background(inputColor)
                                .border(1.dp, if (focusedIndex == index) focusedInputBorder else inputBorder, RoundedCornerShape(14.dp))
                                .onFocusChanged { if (it.isFocused) focusedIndex = index }.padding(horizontal = 12.dp, vertical = 13.dp),
                            decorationBox = { field ->
                                Box {
                                    if (value.isEmpty()) Text(
                                        "\u8f93\u5165\u4e00\u884c\u6761\u7801\u5185\u5bb9",
                                        color = secondary,
                                        fontSize = 16.sp,
                                        style = LocalTextStyle.current.copy(background = Color.Transparent),
                                    )
                                    field()
                                }
                            }
                        )
                        if (values.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            SmallInputAction(ArrowCircleUpIcon, "上移", enabled = index > 0, iconSize = 27.dp) {
                                val other = values[index - 1]; values[index - 1] = values[index]; values[index] = other; syncDraft()
                            }
                            SmallInputAction(ArrowCircleDownIcon, "下移", enabled = index < values.lastIndex, iconSize = 27.dp) {
                                val other = values[index + 1]; values[index + 1] = values[index]; values[index] = other; syncDraft()
                            }
                            SmallInputAction(DeleteIcon, "删除", enabled = true, iconTint = if (dark) Color(0xffffb0b0) else Color(0xffe58b8b), iconSize = 24.dp, onLongClick = { clearDialog = true }) {
                                if (values.size == 1) values[0] = "" else values.removeAt(index); syncDraft()
                            }
                        }
                    }
                }
            }
        }

        val count = values.count { it.trim().isNotEmpty() }
        val actionShape = RoundedCornerShape(18.dp)
        val cameraBorder = if (dark) Color(0xff8b929e) else Color(0xff737373)
        val generateEnabled = count > 0
        val generateContainer = if (generateEnabled) {
            if (dark) Color(0xff2d72d9) else Color(0xff2f6fda)
        } else if (dark) {
            Color(0xff3a414c)
        } else {
            Color(0xffd1d1d6)
        }
        val generateContent = if (generateEnabled) Color.White else if (dark) Color(0xffaeb7c5) else Color(0xff99999f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.weight(1f).globalButtonChrome(actionShape, 2.dp).height(52.dp)
                    .clip(actionShape).background(cardColor).clickable {
                    if (values.size >= 100) onNotice("\u6700\u591a\u4fdd\u7559 100 \u884c\u8f93\u5165\u6846")
                    else { val at = (focusedIndex + 1).coerceIn(0, values.size); values.add(at, ""); focusedIndex = at; syncDraft() }
                }, contentAlignment = Alignment.Center
            ) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(AddIcon, "添加一行", Modifier.size(20.dp), tint = textColor); Spacer(Modifier.width(4.dp)); Text("添加一行", color = textColor, fontSize = 15.sp) } }
            Box(
                modifier = Modifier.weight(1f).globalButtonChrome(actionShape, 2.dp).height(52.dp)
                    .clip(actionShape).background(Color.Transparent).border(1.dp, cameraBorder, actionShape).clickable(onClick = onCaptureText),
                contentAlignment = Alignment.Center
            ) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(PhotoCameraIcon, "拍照取字", Modifier.size(22.dp), tint = if (dark) Color(0xff8fc1ff) else Color(0xff246fc4)); Spacer(Modifier.width(6.dp)); Text("拍照取字", color = if (dark) Color(0xff8fc1ff) else Color(0xff246fc4), fontSize = 15.sp) } }
        }

        Box {
            Row(
                Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(18.dp)).background(cardColor).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u6761\u7801\u7c7b\u578b", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), style = LocalTextStyle.current.copy(background = Color.Transparent))
                Box {
                    Button(
                        onClick = { formatExpanded = true },
                        modifier = Modifier.onGloballyPositioned { formatButtonWidth = it.size.width },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (dark) Color(0xff233246) else Color(0xffeef3f9),
                            contentColor = textColor,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Text(formatName, color = textColor, fontSize = 15.sp, maxLines = 1, softWrap = false, style = LocalTextStyle.current.copy(background = Color.Transparent))
                    }
                    AnchoredDropdownMenu(
                        dark = dark,
                        expanded = formatExpanded,
                        onDismissRequest = { formatExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = if (dark) Color(0xff252a33).copy(alpha = .98f) else Color.White.copy(alpha = .94f),
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp,
                        menuWidth = (formatAnchorWidth ?: 148.dp).coerceAtLeast(148.dp),
                        anchorWidth = formatAnchorWidth,
                        alignEndWithAnchor = true,
                    ) {
                        barcodeFormats.forEachIndexed { index, (name, _) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            DropdownMenuItem(text = { Text(name, maxLines = 1, softWrap = false) }, onClick = { formatName = name; viewModel.updateGenerateFormat(name); formatExpanded = false })
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().globalButtonChrome(actionShape, 2.dp).height(52.dp)
                .clip(actionShape).background(generateContainer).clickable(enabled = generateEnabled) {
                syncDraft()
                viewModel.updateGenerateFormat(formatName)
                val result = viewModel.generateBarcodes(formatName)
                if (!result.isValid) {
                    val message = result.errorMessage
                    if (message == "请输入内容") onNotice(message)
                    else onNotice("第 ${result.errorIndex + 1} 行：$message")
                } else {
                }
            },
            contentAlignment = Alignment.Center
        ) { Text("\u751f\u6210 $count \u4e2a\u6761\u7801", color = generateContent, fontSize = 16.sp) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmallInputAction(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, enabled: Boolean, iconTint: Color = Color(0xff667085), iconSize: androidx.compose.ui.unit.Dp = 20.dp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) iconTint else Color(0xffb5bdc9), modifier = Modifier.size(iconSize))
    }
}
