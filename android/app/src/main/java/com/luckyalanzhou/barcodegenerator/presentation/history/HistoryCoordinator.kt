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
    private val persistItems: (List<CodeItem>) -> Unit,
) {
    fun deleteBatch(batch: List<CodeItem>) {
        val ids = batch.map { it.id }.toSet()
        if (ids.isEmpty()) return
        store.edit { items.filter { it.id in ids }.forEach { it.inHistory = false } }
        persistItems(store.itemsSnapshot())
    }

    fun clearHistory() {
        store.edit { items.forEach { it.inHistory = false } }
    }
}
