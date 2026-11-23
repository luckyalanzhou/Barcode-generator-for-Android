package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.component.SingleChoiceMenuItem
import com.luckyalanzhou.barcodegenerator.icons.UnfoldMoreIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.CircularProgressIndicator

import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.domain.MAX_GENERATE_INPUT_ROWS


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.BoxWithConstraints

private data class DeletedGenerateInput(val index: Int, val text: String, val key: Long, val placeholderKey: Long? = null)

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
    isDataLoadFailed: Boolean = false,
    onRetryDataLoad: () -> Unit = {},
    validationError: GenerateInputError? = null,
) {
    val localizedResources = LocalResources.current
    val batchPasteFocusRequester = remember { FocusRequester() }
    val values = remember {
        mutableStateListOf<String>().apply {
            addAll(inputDraft.ifEmpty { listOf("") })
        }
    }
    var focusedIndex by rememberSaveable { mutableIntStateOf(-1) }
    val rowKeys = remember { mutableStateListOf<Long>().apply { addAll(values.indices.map { it.toLong() }) } }
    var nextRowKey by remember { mutableStateOf(values.size.toLong()) }
    var focusRequest by remember { mutableIntStateOf(0) }
    var focusRequestRowKey by remember { mutableStateOf<Long?>(null) }
    var deletedInput by remember { mutableStateOf<DeletedGenerateInput?>(null) }
    fun resetRowKeys() {
        rowKeys.clear()
        rowKeys.addAll(values.map { nextRowKey++ })
        deletedInput = null
    }
    LaunchedEffect(validationError) {
        validationError?.takeIf { it.rowIndex in values.indices }?.let {
            focusedIndex = it.rowIndex
            focusRequestRowKey = rowKeys[it.rowIndex]
            focusRequest++
        }
    }
    var formatName by remember(initialFormat) { mutableStateOf(initialFormat) }
    var formatExpanded by remember { mutableStateOf(false) }
    var openFormatAfterImeDismiss by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var batchPasteDialog by remember { mutableStateOf(false) }
    var batchPasteText by remember { mutableStateOf("") }
    var batchPastePreview by remember { mutableStateOf<BatchBarcodePastePreview>(BatchBarcodePastePreview.Empty) }
    var formatButtonWidth by remember { mutableIntStateOf(0) }
    var formatButtonHeight by remember { mutableIntStateOf(0) }
    val formatInteractions = remember { MutableInteractionSource() }
    val themeColors = LocalAppColorScheme.current
    val textColor = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val inputColor = themeColors.surfaces.input
    val inputBorder = themeColors.borders.input
    val focusedInputBorder = themeColors.borders.focusedInput
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 输入法收起会改变可用窗口高度；先等尺寸稳定，再创建 Popup，避免菜单按旧窗口位置定位。
    LaunchedEffect(openFormatAfterImeDismiss) {
        if (!openFormatAfterImeDismiss) return@LaunchedEffect
        focusManager.clearFocus()
        keyboardController?.hide()
        snapshotFlow { imeInsets.getBottom(density) }.first { it == 0 }
        withFrameNanos { }
        formatExpanded = true
        openFormatAfterImeDismiss = false
    }
    val formatAnchorWidth = formatButtonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } }

    fun syncDraft() { onDraftChanged(values.toList()) }

    if (batchPasteDialog) {
        var showPasteHelp by rememberSaveable { mutableStateOf(false) }
        Dialog(onDismissRequest = { batchPasteDialog = false }) {
            val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            androidx.compose.runtime.SideEffect {
                window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            ComposeGlassDialogCard(dark) {
                Text(stringResource(R.string.batch_paste), color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.batch_paste_one_per_line),
                        modifier = Modifier.padding(top = 6.dp),
                        color = secondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    val previewText = when (val preview = batchPastePreview) {
                        BatchBarcodePastePreview.Empty -> null
                        is BatchBarcodePastePreview.Ready -> pluralStringResource(R.plurals.batch_paste_preview, preview.addedCount, preview.addedCount, preview.totalCount)
                        is BatchBarcodePastePreview.Invalid -> {
                            val reason = preview.rowLimit?.let { stringResource(preview.reason, it) }
                                ?: stringResource(preview.reason)
                            preview.line?.let { stringResource(R.string.row_error, it, reason) } ?: reason
                        }
                    }
                    if (previewText != null) Text(
                        previewText,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (batchPastePreview is BatchBarcodePastePreview.Invalid) themeColors.text.destructive else secondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .heightIn(min = 120.dp, max = 200.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(inputColor)
                            .border(1.dp, inputBorder, RoundedCornerShape(14.dp))
                            .pointerInput(batchPasteFocusRequester) {
                                detectTapGestures { batchPasteFocusRequester.requestFocus() }
                            }
                            .padding(12.dp),
                    ) {
                        BasicTextField(
                            value = batchPasteText,
                            onValueChange = {
                                batchPasteText = it
                                batchPastePreview = BatchBarcodeTextParser.preview(values.toList(), it)
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(batchPasteFocusRequester)
                                .verticalScroll(rememberScrollState())
                                .semantics { contentDescription = localizedResources.getString(R.string.batch_paste_contents) },
                            textStyle = TextStyle(color = textColor, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = MiSansFontFamily),
                            cursorBrush = SolidColor(textColor),
                            decorationBox = { field ->
                                Box {
                                    if (batchPasteText.isEmpty()) {
                                        Text(stringResource(R.string.batch_paste_placeholder), color = secondary, fontSize = 15.sp)
                                    }
                                    field()
                                }
                            },
                        )
                    }
                    TextButton(onClick = { showPasteHelp = !showPasteHelp }) {
                        Text(stringResource(if (showPasteHelp) R.string.batch_paste_hide_rules else R.string.batch_paste_show_rules))
                    }
                    if (showPasteHelp) {
                        Text(stringResource(R.string.batch_paste_help), color = secondary, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogAction(stringResource(R.string.action_cancel), dark, { batchPasteDialog = false })
                    DialogAction(
                        stringResource(R.string.action_add),
                        dark,
                        {
                            when (val parsed = BatchBarcodeTextParser.parse(batchPasteText)) {
                                is BatchBarcodeTextResult.Failure -> {
                                    val reason = parsed.rowLimit?.let { localizedResources.getString(parsed.reason, it) }
                                        ?: localizedResources.getString(parsed.reason)
                                    onNotice(localizedResources.getString(R.string.row_error, parsed.line, reason))
                                }
                                is BatchBarcodeTextResult.Success -> when (
                                    val appended = BatchBarcodeTextParser.append(values.toList(), parsed.values)
                                ) {
                                    BatchBarcodeAppendResult.CapacityExceeded -> onNotice(localizedResources.getString(R.string.batch_paste_capacity_exceeded, BatchBarcodeTextParser.MAX_INPUT_ROWS))
                                    is BatchBarcodeAppendResult.Success -> {
                                        values.clear()
                                        values.addAll(appended.values)
                                        resetRowKeys()
                                        focusedIndex = values.lastIndex
                                        focusRequestRowKey = rowKeys.last()
                                        focusRequest++
                                        syncDraft()
                                        batchPasteText = ""
                                        batchPastePreview = BatchBarcodePastePreview.Empty
                                        batchPasteDialog = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.padding(start = 10.dp),
                        primary = true,
                        enabled = batchPastePreview is BatchBarcodePastePreview.Ready,
                    )
                }
            }
        }
    }

    LaunchedEffect(inputDraft) {
        val incoming = inputDraft.ifEmpty { listOf("") }
        if (values.toList() != incoming) {
            values.clear()
            values.addAll(incoming)
            resetRowKeys()
            focusRequestRowKey = null
            focusedIndex = focusedIndex.coerceIn(-1, values.lastIndex)
        }
    }

    // 明确的清空全部入口进入确认；单行删除另有撤销。
    if (clearDialog) {
        Dialog(
            onDismissRequest = { clearDialog = false },
        ) {
            ComposeGlassDialogCard(dark) {
                Text(
                    stringResource(R.string.clear_inputs_title),
                    color = themeColors.text.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(pluralStringResource(R.plurals.clear_inputs_confirm, values.size, values.size), color = secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogAction(stringResource(R.string.action_cancel), dark, { clearDialog = false })
                    DialogAction(
                        stringResource(R.string.action_clear_all),
                        dark,
                        {
                            values.clear()
                            values.add("")
                            resetRowKeys()
                            focusedIndex = -1
                            focusRequestRowKey = null
                            focusManager.clearFocus()
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

    BoxWithConstraints(Modifier.fillMaxWidth().imePadding()) {
        val keyboardVisible = imeInsets.getBottom(density) > 0
        val keyboardGap = if (keyboardVisible) 12.dp else 0.dp
        val editingHeight = (maxHeight - keyboardGap).coerceAtLeast(0.dp)
        Column(
            Modifier.fillMaxWidth()
                .padding(bottom = keyboardGap)
                .verticalScroll(rememberScrollState())
                .heightIn(
                    min = if (keyboardVisible) editingHeight else 0.dp,
                    max = editingHeight,
                )
                .padding(bottom = if (keyboardVisible) 0.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.barcode_contents), modifier = Modifier.weight(1f), color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                TextButton(onClick = { clearDialog = true }, enabled = values.size > 1 || values.any { it.isNotEmpty() }) {
                    Text(stringResource(R.string.action_clear_all), color = if (values.size > 1 || values.any { it.isNotEmpty() }) themeColors.text.destructive else themeColors.text.disabled, fontSize = 14.sp)
                }
            }
            // 修改、上下移动和删除输入行都会同步草稿，保证离开页面后可恢复。
            ComposeGenerateInputPanel(
                modifier = Modifier.weight(1f, fill = false),
                maxPanelHeight = editingHeight,
                fitWholeRows = !keyboardVisible,
                values = values,
                dark = dark,
                focusedIndex = focusedIndex,
                rowKeys = rowKeys,
                focusRequest = focusRequest,
                focusRequestRowKey = focusRequestRowKey,
                validationError = validationError,
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
                        val otherKey = rowKeys[index - 1]
                        rowKeys[index - 1] = rowKeys[index]
                        rowKeys[index] = otherKey
                        focusRequestRowKey = null
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
                        val otherKey = rowKeys[index + 1]
                        rowKeys[index + 1] = rowKeys[index]
                        rowKeys[index] = otherKey
                        focusRequestRowKey = null
                        focusedIndex = when (focusedIndex) {
                            index -> index + 1
                            index + 1 -> index
                            else -> focusedIndex
                        }
                        syncDraft()
                    }
                },
                onDelete = { index ->
                    if (index in values.indices) {
                        val removed = DeletedGenerateInput(index, values[index], rowKeys[index])
                        values.removeAt(index)
                        rowKeys.removeAt(index)
                        var placeholder: Long? = null
                        if (values.isEmpty()) {
                            values.add("")
                            placeholder = nextRowKey++
                            rowKeys.add(placeholder)
                        }
                        deletedInput = removed.copy(placeholderKey = placeholder)
                        focusedIndex = index.coerceAtMost(values.lastIndex)
                        focusRequestRowKey = rowKeys[focusedIndex]
                        focusRequest++
                        syncDraft()
                    }
                },
            )
            deletedInput?.let { deleted ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.input_row_deleted, deleted.index + 1), color = secondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val placeholder = deleted.placeholderKey?.let { rowKeys.indexOf(it) }?.takeIf { it >= 0 && values[it].isEmpty() }
                        if (values.size - (if (placeholder != null) 1 else 0) >= MAX_GENERATE_INPUT_ROWS) {
                            onNotice(localizedResources.getString(R.string.input_undo_capacity_exceeded))
                        } else {
                            if (placeholder != null) { values.removeAt(placeholder); rowKeys.removeAt(placeholder) }
                            val at = deleted.index.coerceIn(0, values.size)
                            values.add(at, deleted.text)
                            rowKeys.add(at, deleted.key)
                            focusedIndex = at
                            focusRequestRowKey = null
                            focusManager.clearFocus()
                            deletedInput = null
                            syncDraft()
                        }
                    }) { Text(stringResource(R.string.action_undo), color = themeColors.controls.accent, fontSize = 14.sp) }
                }
            }

            val count = values.count { it.trim().isNotEmpty() }
            val generateEnabled = count > 0 && isDataReady && !isPreparingResult
            val generateContainer = if (generateEnabled) themeColors.controls.primaryAction else themeColors.controls.disabledContainer
            val generateContent = if (generateEnabled) themeColors.text.onAccent else themeColors.text.disabled
            GlassGenerateActionBar(
                onAddRow = {
                    if (values.size >= MAX_GENERATE_INPUT_ROWS) onNotice(localizedResources.getString(R.string.input_capacity_exceeded, MAX_GENERATE_INPUT_ROWS))
                    else {
                        val at = (focusedIndex + 1).coerceIn(0, values.size)
                        values.add(at, "")
                        val key = nextRowKey++
                        rowKeys.add(at, key)
                        focusedIndex = at
                        focusRequestRowKey = key
                        focusRequest++
                        syncDraft()
                    }
                },
                onBatchPaste = {
                    batchPasteText = ""
                    batchPastePreview = BatchBarcodePastePreview.Empty
                    batchPasteDialog = true
                },
                onCaptureText = onCaptureText,
            )
            // 条码类型按钮只负责展开菜单；选中后同步更新本地显示和上层保存的格式。
            val formatDescription = barcodeFormats.firstOrNull { it.displayName == formatName }?.let { stringResource(it.descriptionResId) }.orEmpty()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.barcode_format), color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(formatDescription, color = secondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Box {
                    Button(
                        onClick = { openFormatAfterImeDismiss = true },
                        interactionSource = formatInteractions,
                        modifier = Modifier.iosPressFeedback(formatInteractions).onGloballyPositioned {
                            formatButtonWidth = it.size.width
                            formatButtonHeight = it.size.height
                        }
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = localizedResources.getString(R.string.barcode_format)
                                stateDescription = localizedResources.getString(R.string.control_state_with_value,
                                    localizedResources.getString(R.string.control_state_with_value, formatName, formatDescription),
                                    localizedResources.getString(if (formatExpanded) R.string.state_expanded else R.string.state_collapsed))
                            },
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = themeColors.text.secondary,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Text(formatName, color = themeColors.text.secondary, fontSize = 15.sp, maxLines = 1, softWrap = false, style = LocalTextStyle.current.copy(background = Color.Transparent))
                        Icon(UnfoldMoreIcon, contentDescription = null, tint = themeColors.text.secondary,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    AnchoredDropdownMenu(
                        dark = dark,
                        expanded = formatExpanded,
                        onDismissRequest = { formatExpanded = false },
                        shape = RoundedCornerShape(MenuMetrics.surfaceCornerRadius),
                        containerColor = themeColors.surfaces.panel,
                        tonalElevation = 0.dp,
                        shadowElevation = MenuMetrics.surfaceShadowElevation,
                        menuWidth = (formatAnchorWidth ?: 176.dp).coerceAtLeast(176.dp),
                        anchorWidth = formatAnchorWidth,
                        anchorHeight = with(density) { formatButtonHeight.toDp() },
                        alignEndWithAnchor = true,
                        // 与外观/字符纠错一致：整个菜单从按钮边界展开，不走 Material 默认动画。
                        cornerReveal = true,
                        dynamicBackdrop = true,
                    ) {
                        barcodeFormats.forEachIndexed { index, option ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            SingleChoiceMenuItem(option.displayName, option.displayName == formatName, highlightSelection = false) {
                                formatName = option.displayName
                                onFormatChanged(option.displayName)
                                formatExpanded = false
                            }
                        }
                    }
                }
            }

            // 主操作满足前置条件后才提交输入；准备期间显示进度并阻止重复提交。
            val generateInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier.fillMaxWidth().iosPressFeedback(generateInteraction)
                    .globalButtonChrome(RoundedCornerShape(18.dp)).heightIn(min = 52.dp)
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
                    if (isPreparingResult) stringResource(R.string.preparing_all_barcodes) else if (validationError != null) stringResource(R.string.regenerate) else if (count == 0) stringResource(R.string.generate_barcode) else pluralStringResource(R.plurals.generate_barcode_count, count, count),
                    color = generateContent,
                    fontSize = 16.sp,
                    style = LocalTextStyle.current.copy(background = Color.Transparent),
                )
                }
            }

            if (isDataLoadFailed) {
                Text(
                    stringResource(R.string.generate_data_load_failed),
                    color = themeColors.text.primary,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                DialogAction(
                    stringResource(R.string.action_retry_load),
                    dark,
                    onRetryDataLoad,
                    modifier = Modifier.align(Alignment.End),
                    primary = true,
                )
            }
        }
    }
}
