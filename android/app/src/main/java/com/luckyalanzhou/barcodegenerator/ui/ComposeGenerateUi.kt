package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.BarcodeViewModel
import com.luckyalanzhou.barcodegenerator.BarcodeEvent
import com.luckyalanzhou.barcodegenerator.barcodeFormats

import com.luckyalanzhou.barcodegenerator.icons.AddIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.PhotoCameraIcon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.window.Dialog
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
    val themeColors = LocalBarcodeThemeColors.current
    val textColor = themeColors.primary
    val secondary = themeColors.secondary
    val cardColor = themeColors.panel
    val inputColor = themeColors.input
    val cardBorder = themeColors.cardBorder
    val inputBorder = themeColors.inputBorder
    val focusedInputBorder = themeColors.focusedInputBorder
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
        Dialog(
            onDismissRequest = { clearDialog = false },
        ) {
            ComposeGlassDialogCard(dark) {
                Text(
                    "\u6e05\u7a7a\u6240\u6709\u8f93\u5165\uff1f",
                    color = themeColors.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogAction("取消", dark, { clearDialog = false })
                    DialogAction(
                        "确定",
                        dark,
                        {
                            values.clear()
                            values.add("")
                            syncDraft()
                            clearDialog = false
                        },
                        modifier = Modifier.padding(start = 20.dp),
                        destructive = true,
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ComposeGenerateInputPanel(
            values = values,
            dark = dark,
            focusedIndex = focusedIndex,
            onValueChange = { index, value -> values[index] = value; syncDraft() },
            onFocus = { focusedIndex = it },
            onMoveUp = {
                index ->
                val other = values[index - 1]
                values[index - 1] = values[index]
                values[index] = other
                focusedIndex = when (focusedIndex) {
                    index -> index - 1
                    index - 1 -> index
                    else -> focusedIndex
                }
                syncDraft()
            },
            onMoveDown = {
                index ->
                val other = values[index + 1]
                values[index + 1] = values[index]
                values[index] = other
                focusedIndex = when (focusedIndex) {
                    index -> index + 1
                    index + 1 -> index
                    else -> focusedIndex
                }
                syncDraft()
            },
            onDelete = {
                index ->
                if (values.size == 1) {
                    values[0] = ""
                    focusedIndex = 0
                } else {
                    values.removeAt(index)
                    focusedIndex = when {
                        focusedIndex == index -> (index - 1).coerceAtLeast(0).coerceAtMost(values.lastIndex)
                        focusedIndex > index -> focusedIndex - 1
                        else -> focusedIndex
                    }
                }
                syncDraft()
            },
            onDeleteLongClick = { clearDialog = true },
        )

        val count = values.count { it.trim().isNotEmpty() }
        val generateEnabled = count > 0
        val generateContainer = if (generateEnabled) themeColors.progress else themeColors.button
        val generateContent = if (generateEnabled) themeColors.onAccent else themeColors.disabled
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComposeGenerateActionButton(
                icon = AddIcon,
                iconDescription = "添加一行",
                label = "添加一行",
                containerColor = cardColor,
                contentColor = textColor,
                modifier = Modifier.weight(1f),
                iconSize = 20.dp,
                contentSpacing = 4.dp,
                onClick = {
                    if (values.size >= 100) onNotice("\u6700\u591a\u4fdd\u7559 100 \u884c\u8f93\u5165\u6846")
                    else { val at = (focusedIndex + 1).coerceIn(0, values.size); values.add(at, ""); focusedIndex = at; syncDraft() }
                },
            )
            ComposeGenerateActionButton(
                icon = PhotoCameraIcon,
                iconDescription = "拍照填充",
                label = "拍照填充",
                // 与“添加一行”共用同一张卡片容器，避免单独的描边造成外观不一致。
                containerColor = cardColor,
                contentColor = themeColors.link,
                modifier = Modifier.weight(1f),
                onClick = onCaptureText,
            )
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
                        modifier = Modifier.onGloballyPositioned { formatButtonWidth = it.size.width }
                            .height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, themeColors.buttonBorder),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeColors.button,
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
                        containerColor = themeColors.surfaceOverlay,
                        tonalElevation = 0.dp,
                        shadowElevation = 1.dp,
                        menuWidth = (formatAnchorWidth ?: 148.dp).coerceAtLeast(148.dp),
                        anchorWidth = formatAnchorWidth,
                        alignEndWithAnchor = true,
                    ) {
                        barcodeFormats.forEachIndexed { index, (name, _) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            DropdownMenuItem(modifier = Modifier.height(40.dp), text = { Text(name, color = themeColors.primary, maxLines = 1, softWrap = false) }, onClick = { formatName = name; viewModel.updateGenerateFormat(name); formatExpanded = false })
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().globalButtonChrome(RoundedCornerShape(18.dp), 2.dp).height(52.dp)
                .clip(RoundedCornerShape(18.dp)).background(generateContainer).clickable(enabled = generateEnabled) {
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
private fun SmallInputAction(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, enabled: Boolean, iconTint: Color? = null, iconSize: androidx.compose.ui.unit.Dp = 20.dp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val themeColors = LocalBarcodeThemeColors.current
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) iconTint ?: themeColors.secondary else themeColors.disabled, modifier = Modifier.size(iconSize))
    }
}
