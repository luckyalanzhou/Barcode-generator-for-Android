package com.luckyalanzhou.barcodegenerator

import android.graphics.Color
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun HistoryComposePage(
    entries: List<Pair<Long, List<CodeItem>>>,
    dark: Boolean,
    onClear: () -> Unit,
    onOpen: (List<CodeItem>) -> Unit,
    onEdit: (List<CodeItem>) -> Unit,
    onDelete: (List<CodeItem>) -> Unit,
    timeText: (Long) -> String
) {
    val primary = if (dark) ComposeColor(0xfff2f4f8) else ComposeColor(0xff182230)
    val secondary = if (dark) ComposeColor(0xffaeb9c9) else ComposeColor(0xff6b7280)
    val card = if (dark) ComposeColor(0xff1b222d) else ComposeColor(0xfff7f9fc)
    val hapticView = LocalView.current
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.TextButton(onClick = onClear) { Text("清空", color = if (dark) ComposeColor(0xffffa0a0) else ComposeColor(0xffc85c5c)) }
        }
        if (entries.isEmpty()) {
            BoxedEmptyHistory(dark)
        } else {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                entries.forEach { (time, originalBatch) ->
                    val batch = originalBatch.sortedBy { it.id }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)
                            .combinedClickable(
                                onClick = { onOpen(batch) },
                                onLongClick = {
                                    hapticView.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.LONG_PRESS,
                                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                    )
                                    onEdit(batch)
                                },
                            ),
                        shape = RoundedCornerShape(12.dp), color = card, tonalElevation = 0.dp, shadowElevation = 0.dp
                    ) {
                        Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${batch.size}条：${batch.firstOrNull()?.text?.let { if (it.length > 8) it.take(8) + "..." else it }.orEmpty()}",
                                modifier = Modifier.weight(1f), color = primary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(timeText(time), color = secondary, fontSize = 12.sp, maxLines = 1)
                            Spacer(Modifier.width(2.dp))
                            IconButton(onClick = { onDelete(batch) }, modifier = Modifier.size(36.dp)) {
                                Icon(painterResource(R.drawable.ic_delete_light), "删除这条历史记录", tint = ComposeColor(0xffd98787))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxedEmptyHistory(dark: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("暂无历史记录", color = if (dark) ComposeColor(0xffaeb9c9) else ComposeColor(0xff6b7280), fontSize = 17.sp)
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
            color = if (dark) ComposeColor(0xfff2f4f8) else ComposeColor(0xff182230),
            fontSize = 20.sp,
        )
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

internal fun MainActivity.showHistoryBatchPickerCompose(batch: List<CodeItem>) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        HistoryBatchPickerDialogContent(
            batch = batch,
            dark = isDark(),
            onDismiss = dismiss,
            onEdit = { item -> window.decorView.post { showItemEditorCompose(item) } },
        )
    }
}
