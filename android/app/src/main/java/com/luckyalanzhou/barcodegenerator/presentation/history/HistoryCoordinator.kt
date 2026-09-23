package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem

/**
 * Coordinates history-only mutations without taking ownership of Compose state.
 * The ViewModel remains the public state/event boundary and supplies persistence.
 */
internal class HistoryCoordinator(
    private val store: FavoritesStateStore,
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
        persistItems(store.itemsSnapshot())
    }
}
