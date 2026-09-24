package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesStateStore

/** Coordinates barcode-item edits that can affect both favorite groups and history. */
internal class BarcodeItemMutationCoordinator(
    private val store: FavoritesStateStore,
    private val persistAllFavorites: () -> Unit,
    private val persistItems: () -> Unit,
) {
    fun deleteItem(itemId: Long) {
        store.edit {
            items.removeAll { it.id == itemId }
            val modifiedAt = System.currentTimeMillis()
            groups.indices.filter { itemId in groups[it].itemIds }.forEach { index ->
                val group = groups[index]
                group.itemIds.removeAll { it == itemId }
                groups[index] = group.copy(savedAt = modifiedAt)
            }
        }
        persistAllFavorites()
    }

    fun updateItem(itemId: Long, text: String, format: String) {
        val favorite = store.edit {
            val item = items.firstOrNull { it.id == itemId } ?: return@edit null
            item.text = text
            item.format = format
            val modifiedAt = System.currentTimeMillis()
            groups.indices.filter { itemId in groups[it].itemIds }.forEach { index ->
                groups[index] = groups[index].copy(savedAt = modifiedAt)
            }
            item.favorite
        } ?: return
        if (favorite) persistAllFavorites() else persistItems()
    }
}
