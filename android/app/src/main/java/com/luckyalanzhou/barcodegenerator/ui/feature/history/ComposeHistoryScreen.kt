package com.luckyalanzhou.barcodegenerator.ui.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.luckyalanzhou.barcodegenerator.domain.CodeItem

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
) {
    val refreshTime = remember(refreshGeneration) { System.currentTimeMillis() }
    val entries = remember(items, refreshGeneration) {
        items
            .asSequence()
            .filter { it.inHistory }
            .map { it.copy() }
            .groupBy { it.createdAt }
            .toList()
            .sortedByDescending { it.first }
    }

    HistoryComposePage(
        entries = entries,
        refreshGeneration = refreshGeneration,
        dark = dark,
        onOpen = onOpen,
        onEdit = onEdit,
        onDelete = onDelete,
        timeText = { time -> formatHistoryTime(time, refreshTime) },
    )
}
