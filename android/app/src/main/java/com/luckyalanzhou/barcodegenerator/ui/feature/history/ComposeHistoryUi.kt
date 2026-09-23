package com.luckyalanzhou.barcodegenerator.ui.feature.history

import com.luckyalanzhou.barcodegenerator.ui.feature.editor.showItemEditorCompose
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.app.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.globalCardSurface

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.domain.CodeItem

import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatHistoryTime(time: Long): String {
    val date = Date(time)
    val now = Date()
    val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return if (day.format(date) == day.format(now)) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(date)
    }
}

/** 历史页：单一 LazyColumn 承载标题、空状态和历史批次，避免外层嵌套滚动。 */
@Composable
internal fun HistoryComposePage(
    entries: List<Pair<Long, List<CodeItem>>>,
    dark: Boolean,
    onClear: () -> Unit,
    onOpen: (List<CodeItem>) -> Unit,
    onEdit: (List<CodeItem>) -> Unit,
    onDelete: (List<CodeItem>) -> Unit,
    timeText: (Long) -> String,
) {
    val themeColors = LocalAppColorScheme.current
    val primary = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val clearColor = themeColors.text.destructive
    val hapticView = LocalView.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "history-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClear) {
                    Text("一键清空", color = clearColor)
                }
            }
        }

        if (entries.isEmpty()) {
            item(key = "history-empty") {
                HistoryEmptyState(color = secondary)
            }
        } else {
            items(
                items = entries,
                key = { (time, _) -> "history-$time" },
                contentType = { "history-batch" },
            ) { (time, originalBatch) ->
                val batch = originalBatch.sortedBy { it.id }
                HistoryBatchCard(
                    batch = batch,
                    time = timeText(time),
                    dark = dark,
                    primary = primary,
                    secondary = secondary,
                    onOpen = { onOpen(batch) },
                    onEdit = {
                        hapticView.performHapticFeedback(
                            android.view.HapticFeedbackConstants.LONG_PRESS,
                        )
                        onEdit(batch)
                    },
                    onDelete = { onDelete(batch) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryBatchCard(
    batch: List<CodeItem>,
    time: String,
    dark: Boolean,
    primary: Color,
    secondary: Color,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val card = LocalAppColorScheme.current.surfaces.card
    val preview = batch.firstOrNull()?.text.orEmpty().let { text ->
        if (text.length > 8) text.take(8) + "..." else text
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .globalCardSurface(dark, card, RoundedCornerShape(12.dp), 2.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        color = card,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${batch.size}条：$preview",
                modifier = Modifier.weight(1f),
                color = primary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(time, color = LocalAppColorScheme.current.text.placeholder, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(DeleteIcon, "删除这条历史记录", tint = LocalAppColorScheme.current.text.destructive)
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(color: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("暂无历史记录", color = color, fontSize = 17.sp)
    }
}

@Composable
internal fun HistoryBatchPickerDialogContent(
    batch: List<CodeItem>,
    dark: Boolean,
    onDismiss: () -> Unit,
    onEdit: (CodeItem) -> Unit,
) {
    ComposeGlassDialogCard(dark) {
        Text(
            "本次生成的 ${batch.size} 个条码",
            color = LocalAppColorScheme.current.text.primary,
            fontSize = 20.sp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            batch.forEach { item ->
                DialogAction(
                    item.text,
                    dark,
                    {
                        onDismiss()
                        onEdit(item)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

internal fun MainActivity.showHistoryBatchPickerCompose(batch: List<CodeItem>) {
    showComposeDialog(compact = false) { dismiss ->
        HistoryBatchPickerDialogContent(
            batch = batch,
            dark = isDark(),
            onDismiss = dismiss,
            onEdit = { item -> window.decorView.post { showItemEditorCompose(item, viewModel::deleteBarcodeItem, viewModel::updateBarcodeItem) } },
        )
    }
}
