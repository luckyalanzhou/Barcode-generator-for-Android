package com.luckyalanzhou.barcodegenerator.presentation.history

import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.CodeItem

/**
 * 只协调历史记录相关变更，不接管 Compose 状态。
 * ViewModel 仍是公开的状态/事件边界，并负责提供持久化能力。
 */
internal class HistoryCoordinator(
    private val store: LibraryStateStore,
    private val persistDeletion: (String, Long) -> Unit,
) {
    fun deleteBatch(batch: List<CodeItem>) {
        val first = batch.firstOrNull() ?: return
        val batchId = first.historyBatchId
        store.edit {
            items.filter { item ->
                item.historyBatchId == batchId && (batchId.isNotEmpty() || item.createdAt == first.createdAt)
            }.forEach { it.inHistory = false }
        }
        persistDeletion(batchId, first.createdAt)
    }

    fun clearHistory() {
        store.edit { items.forEach { it.inHistory = false } }
    }
}
