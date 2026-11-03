package com.luckyalanzhou.barcodegenerator.ui.feature.history

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.delay

/** 以本地日历边界刷新历史时间锚点，避免页面跨午夜后仍把昨天当作今天。 */
internal fun millisUntilNextHistoryDayChange(
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long {
    val nextDay = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
    return (nextDay - nowMillis).coerceAtLeast(1L)
}

/**
 * 历史页数据适配层：仅保留历史条码，按生成时间归并为批次并按时间倒序展示。
 * 查看、长按编辑和删除操作均交给上层处理，页面不直接修改持久化数据。
 */
@Composable
internal fun HistoryScreen(
    items: List<CodeItem>,
    refreshGeneration: Long,
    dark: Boolean,
    onOpen: (List<CodeItem>) -> Unit,
    onEdit: (List<CodeItem>) -> Unit,
    onDelete: (List<CodeItem>) -> Unit,
    isReady: Boolean = true,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val context = LocalContext.current
    var timeZoneChangeGeneration by remember { mutableLongStateOf(0L) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action?.let(HISTORY_CLOCK_CHANGE_ACTIONS::contains) == true) {
                    timeZoneChangeGeneration++
                }
            }
        }
        // 历史时间标签依赖本地日历；校时、跨日和时区变化时立即刷新时间锚点。
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply { HISTORY_CLOCK_CHANGE_ACTIONS.forEach(::addAction) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val timeZoneId = TimeZone.getDefault().id
    val refreshTime by produceState(
        System.currentTimeMillis(),
        refreshGeneration,
        timeZoneId,
        timeZoneChangeGeneration,
    ) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            delay(millisUntilNextHistoryDayChange(now))
        }
    }
    val entries = remember(items, refreshGeneration) {
        items.asSequence()
            .filter { it.inHistory }
            .map { it.copy() }
            .groupBy { item ->
                item.historyBatchId.takeIf(String::isNotBlank)
                    ?.let { "batch:$it" }
                    ?: "legacy:${item.createdAt}"
            }
            .map { (key, batch) -> HistoryBatchEntry(key, batch.maxOf { it.createdAt }, batch) }
            .sortedWith(compareByDescending<HistoryBatchEntry> { it.time }.thenByDescending { it.items.maxOf(CodeItem::id) })
    }

    HistoryComposePage(
        entries = entries,
        isReady = isReady,
        loadFailed = loadFailed,
        onRetry = onRetry,
        refreshGeneration = refreshGeneration,
        dark = dark,
        onOpen = onOpen,
        onEdit = onEdit,
        onDelete = onDelete,
        timeText = { time -> formatHistoryTime(time, refreshTime) },
    )
}

private val HISTORY_CLOCK_CHANGE_ACTIONS = setOf(
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_DATE_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
)
