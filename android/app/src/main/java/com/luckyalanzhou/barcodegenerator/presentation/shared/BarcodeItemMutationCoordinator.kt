package com.luckyalanzhou.barcodegenerator.presentation.shared


/** Coordinates barcode-item edits that can affect both favorite groups and history. */
internal class BarcodeItemMutationCoordinator(
    private val store: LibraryStateStore,
    private val persistAllFavorites: () -> Unit,
    private val persistItems: () -> Unit,
    private val persistDeletion: (Long, Long) -> Unit,
) {
    fun deleteItem(itemId: Long) {
        val modifiedAt = System.currentTimeMillis()
        store.edit {
            items.removeAll { it.id == itemId }
            groups.indices.filter { itemId in groups[it].itemIds }.forEach { index ->
                val group = groups[index]
                group.itemIds.removeAll { it == itemId }
                groups[index] = group.copy(savedAt = modifiedAt)
            }
        }
        persistDeletion(itemId, modifiedAt)
    }

    fun updateItem(itemId: Long, text: String, format: String) {
        if (text.isBlank() || !com.luckyalanzhou.barcodegenerator.domain.BarcodeValidator.validate(text, format).valid) return
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
