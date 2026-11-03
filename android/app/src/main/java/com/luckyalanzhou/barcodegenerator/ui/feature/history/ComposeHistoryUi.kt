package com.luckyalanzhou.barcodegenerator.ui.feature.history

import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.luckyalanzhou.barcodegenerator.ui.component.unframedActionPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.groupedContentSurface

import com.luckyalanzhou.barcodegenerator.domain.CodeItem

import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.EditIcon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatHistoryTime(time: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val date = Date(time)
    val now = Date(nowMillis)
    val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return if (day.format(date) == day.format(now)) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(date)
    }
}

internal fun historyBatchSummary(batch: List<CodeItem>): String =
    batch.firstOrNull()?.text.orEmpty().replace('\n', ' ').replace('\r', ' ') +
        if (batch.size > 1) "…" else ""

/** 历史行用持久批次 ID 区分新数据；旧数据继续以时间戳作为兼容身份。 */
internal data class HistoryBatchEntry(
    val key: String,
    val time: Long,
    val items: List<CodeItem>,
)

/**
 * 历史列表界面：空列表时显示空状态，有记录时按批次显示首条内容、时间和删除按钮。
 * 点击卡片查看整批结果，编辑按钮和长按都可进入批次编辑；清空全部历史由历史 Tab 长按菜单触发。
 */
@Composable
internal fun HistoryComposePage(
    entries: List<HistoryBatchEntry>,
    refreshGeneration: Long,
    dark: Boolean,
    onOpen: (List<CodeItem>) -> Unit,
    onEdit: (List<CodeItem>) -> Unit,
    onDelete: (List<CodeItem>) -> Unit,
    timeText: (Long) -> String,
    isReady: Boolean = true,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val themeColors = LocalAppColorScheme.current
    val primary = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val hapticView = LocalView.current
    val reduceMotion = LocalVisualEffectsPolicy.current.reduceMotion
    val listState = rememberLazyListState()
    var appliedRefreshGeneration by rememberSaveable { mutableLongStateOf(refreshGeneration) }
    LaunchedEffect(refreshGeneration) {
        if (appliedRefreshGeneration != refreshGeneration) {
            if (reduceMotion) listState.scrollToItem(0)
            else listState.animateScrollToItem(0)
            appliedRefreshGeneration = refreshGeneration
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!isReady) {
            item(key = "history-loading") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (loadFailed) "历史记录加载失败" else "正在加载历史记录…",
                        color = if (loadFailed) primary else secondary,
                        fontSize = 17.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        textAlign = TextAlign.Center,
                    )
                    if (loadFailed) {
                        DialogAction("重试", dark, onRetry, modifier = Modifier.padding(top = 12.dp), primary = true)
                    }
                }
            }
        }
        if (isReady && entries.isEmpty()) {
            item(key = "history-empty") {
                HistoryEmptyState(color = secondary)
            }
        } else if (entries.isNotEmpty()) {
            items(
                items = entries,
                key = { entry -> "history-${entry.key}" },
                contentType = { "history-batch" },
            ) { entry ->
                val batch = entry.items.sortedBy { it.id }
                HistoryBatchCard(
                    batch = batch,
                    time = timeText(entry.time),
                    dark = dark,
                    primary = primary,
                    onOpen = { onOpen(batch) },
                    onEdit = {
                        hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
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
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColorScheme.current
    val card = colors.surfaces.card
    val deleteInteractions = remember { MutableInteractionSource() }

    // 卡片主体点击用于查看；编辑提供可见按钮并保留长按快捷方式，删除按钮只删除当前批次。
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .groupedContentSurface(dark, card, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onOpen,
                onClickLabel = "查看这批条码",
                onLongClick = onEdit,
                onLongClickLabel = "编辑这批条码",
                role = Role.Button,
                hapticFeedbackEnabled = false,
            ),
        shape = RoundedCornerShape(12.dp),
        color = card,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 15.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(historyBatchSummary(batch), color = primary, fontSize = 16.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            Text(time, color = colors.text.secondary, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp))
            val editInteractions = remember { MutableInteractionSource() }
            IconButton(
                onClick = onEdit,
                interactionSource = editInteractions,
                modifier = Modifier.size(48.dp).unframedActionPressFeedback(editInteractions),
            ) {
                // 只移动图标来缩短视觉间距，按钮本身仍占独立的 48dp 触控区域。
                Icon(EditIcon, "编辑这批条码", tint = colors.controls.accent, modifier = Modifier.offset(x = 12.dp).size(20.dp))
            }
            IconButton(onClick = onDelete, interactionSource = deleteInteractions,
                modifier = Modifier.size(48.dp).unframedActionPressFeedback(deleteInteractions)) {
                Icon(DeleteIcon, "删除这条历史记录", tint = colors.content.deleteIcon, modifier = Modifier.size(20.dp))
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
