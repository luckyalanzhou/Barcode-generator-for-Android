package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.component.SingleChoiceMenuItem
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowDownIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.CircularProgressIndicator

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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 生成页的输入与操作界面：编辑多行内容、选择条码格式，并将确认后的输入交给生成流程。
 * 行编辑、拍照填充和生成分别通过回调交给上层；没有有效输入、数据未就绪或结果准备中时禁用生成。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GenerateContent(
    inputDraft: List<String>,
    initialFormat: String,
    dark: Boolean,
    isPreparingResult: Boolean,
    isDataReady: Boolean,
    onDraftChanged: (List<String>) -> Unit,
    onFormatChanged: (String) -> Unit,
    onGenerate: (List<String>, String) -> Unit,
    onCaptureText: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val values = remember {
        mutableStateListOf<String>().apply {
            addAll(inputDraft.ifEmpty { listOf("") })
        }
    }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var formatName by remember(initialFormat) { mutableStateOf(initialFormat) }
    var formatExpanded by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var formatButtonWidth by remember { mutableIntStateOf(0) }
    val themeColors = LocalAppColorScheme.current
    val textColor = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val cardColor = themeColors.surfaces.panel
    val inputColor = themeColors.surfaces.input
    val inputBorder = themeColors.borders.input
    val focusedInputBorder = themeColors.borders.focusedInput
    val density = LocalDensity.current
    val formatAnchorWidth = formatButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }

    fun syncDraft() { onDraftChanged(values.toList()) }

    LaunchedEffect(inputDraft) {
        val incoming = inputDraft.ifEmpty { listOf("") }
        if (values.toList() != incoming) {
            values.clear()
            values.addAll(incoming)
            focusedIndex = focusedIndex.coerceIn(-1, values.lastIndex)
        }
    }

    // 长按输入行的删除操作会进入二次确认；确认后清空内容，但保留一个可继续输入的空行。
    if (clearDialog) {
        Dialog(
            onDismissRequest = { clearDialog = false },
        ) {
            ComposeGlassDialogCard(dark) {
                Text(
                    "\u6e05\u7a7a\u6240\u6709\u8f93\u5165\uff1f",
                    color = themeColors.text.primary,
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
        // 修改、上下移动和删除输入行都会同步草稿，保证离开页面后可恢复。
        ComposeGenerateInputPanel(
            values = values,
            dark = dark,
            focusedIndex = focusedIndex,
            onValueChange = { index, value ->
                if (index in values.indices) {
                    values[index] = value
                    syncDraft()
                }
            },
            onFocus = { focusedIndex = it },
            onMoveUp = {
                index ->
                if (index in 1 until values.size) {
                    val other = values[index - 1]
                    values[index - 1] = values[index]
                    values[index] = other
                    focusedIndex = when (focusedIndex) {
                        index -> index - 1
                        index - 1 -> index
                        else -> focusedIndex
                    }
                    syncDraft()
                }
            },
            onMoveDown = {
                index ->
                if (index in 0 until values.lastIndex) {
                    val other = values[index + 1]
                    values[index + 1] = values[index]
                    values[index] = other
                    focusedIndex = when (focusedIndex) {
                        index -> index + 1
                        index + 1 -> index
                        else -> focusedIndex
                    }
                    syncDraft()
                }
            },
            onDelete = {
                index ->
                if (index in values.indices) {
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
                }
            },
            onDeleteLongClick = { clearDialog = true },
        )

        val count = values.count { it.trim().isNotEmpty() }
        val generateEnabled = count > 0 && isDataReady && !isPreparingResult
        val generateContainer = if (generateEnabled) themeColors.controls.accent else themeColors.controls.disabledContainer
        val generateContent = if (generateEnabled) themeColors.text.onAccent else themeColors.text.disabled
        // 次要操作：添加行插入到当前焦点之后；拍照填充把识别文字交给上层处理。
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
                // 拍照填充是生成页的主要入口之一：使用柔和强调色突出，
                // 同时让底部实色“生成”按钮继续承担最高优先级。
                containerColor = lerp(cardColor, themeColors.controls.accent, if (dark) .24f else .10f),
                contentColor = if (dark) textColor else themeColors.controls.accent,
                modifier = Modifier.weight(1f),
                onClick = onCaptureText,
            )
        }

        // 条码类型按钮只负责展开菜单；选中后同步更新本地显示和上层保存的格式。
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
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = "条码类型"
                                stateDescription = "$formatName，${if (formatExpanded) "已展开" else "已收起"}"
                            },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, themeColors.borders.button),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeColors.controls.button,
                            contentColor = textColor,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Text(formatName, color = textColor, fontSize = 15.sp, maxLines = 1, softWrap = false, style = LocalTextStyle.current.copy(background = Color.Transparent))
                        Icon(KeyboardArrowDownIcon, contentDescription = null, tint = themeColors.text.secondary,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    AnchoredDropdownMenu(
                        dark = dark,
                        expanded = formatExpanded,
                        onDismissRequest = { formatExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = themeColors.surfaces.overlay,
                        tonalElevation = 0.dp,
                        shadowElevation = 1.dp,
                        menuWidth = (formatAnchorWidth ?: 176.dp).coerceAtLeast(176.dp),
                        anchorWidth = formatAnchorWidth,
                        alignEndWithAnchor = true,
                    ) {
                        barcodeFormats.forEachIndexed { index, option ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            SingleChoiceMenuItem(option.displayName, option.displayName == formatName) {
                                formatName = option.displayName
                                onFormatChanged(option.displayName)
                                formatExpanded = false
                            }
                        }
                    }
                }
            }
        }

        // 主操作满足前置条件后才提交输入；准备期间显示进度并阻止重复提交。
        val generateInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier.fillMaxWidth().iosPressFeedback(generateInteraction)
                .globalButtonChrome(RoundedCornerShape(18.dp), 2.dp).heightIn(min = 52.dp)
                .clip(RoundedCornerShape(18.dp)).background(generateContainer).clickable(enabled = generateEnabled,
                    interactionSource = generateInteraction, indication = null, role = Role.Button) {
                onGenerate(values.toList(), formatName)
            },
            contentAlignment = Alignment.Center
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isPreparingResult) CircularProgressIndicator(Modifier.size(18.dp), color = generateContent, strokeWidth = 2.dp)
            Text(
                if (isPreparingResult) "正在准备全部条码…" else "\u751f\u6210 $count \u4e2a\u6761\u7801",
                color = generateContent,
                fontSize = 16.sp,
            )
            }
        }
    }
}
