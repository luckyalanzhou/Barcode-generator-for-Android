package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeItemMutationCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeItemMutationCoordinatorTest {
    @Test
    fun invalidEditDoesNotChangeStoredItemOrQueueAWrite() {
        val original = CodeItem(1, "ABC", "Code 39", 1)
        val store = LibraryStateStore().apply { replace(listOf(original), emptyList(), emptyList()) }
        var writes = 0
        val coordinator = BarcodeItemMutationCoordinator(store, { writes++ }, { writes++ }, { _, _ -> })
        coordinator.updateItem(1, "lowercase", "Code 39")
        assertEquals("ABC", store.itemsSnapshot().single().text)
        assertEquals(0, writes)
    }
    @Test
    fun deletingItemRemovesAllGroupLinksAndPersistsExplicitDeletion() {
        val store = LibraryStateStore()
        val removed = CodeItem(1, "removed", "CODE_128", 1L, favorite = true, folder = "Work", inHistory = true)
        val retained = CodeItem(2, "retained", "CODE_128", 2L, favorite = true, folder = "Work", inHistory = true)
        store.replace(
            listOf(removed, retained),
            listOf(
                FavoriteGroup(10, "Work", "First", 10L, mutableListOf(1, 2)),
                FavoriteGroup(11, "Work", "Second", 11L, mutableListOf(1)),
            ),
            listOf("Work"),
        )
        var favoriteWrites = 0
        var itemWrites = 0
        val deletedIds = mutableListOf<Long>()
        val coordinator = BarcodeItemMutationCoordinator(
            store = store,
            persistAllFavorites = { favoriteWrites++ },
            persistItems = { itemWrites++ },
            persistDeletion = { id, _ -> deletedIds += id },
        )

        coordinator.deleteItem(removed.id)

        assertEquals(listOf(retained.id), store.itemsSnapshot().map { it.id })
        assertEquals(listOf(listOf(retained.id), emptyList<Long>()), store.groupsSnapshot().map { it.itemIds })
        assertEquals(0, favoriteWrites)
        assertEquals(listOf(removed.id), deletedIds)
        assertEquals(0, itemWrites)
    }

    @Test
    fun updatesFavoriteAndHistoryItemsThroughTheirCorrectPersistencePaths() {
        val favorite = CodeItem(1, "favorite", "CODE_128", 1L, favorite = true, inHistory = true)
        val history = CodeItem(2, "history", "CODE_128", 2L, favorite = false, inHistory = true)
        val store = LibraryStateStore().apply {
            replace(
                listOf(favorite, history),
                listOf(FavoriteGroup(10, "", "Favorite", 10L, mutableListOf(favorite.id))),
                emptyList(),
            )
        }
        var favoriteWrites = 0
        var itemWrites = 0
        val coordinator = BarcodeItemMutationCoordinator(
            store = store,
            persistAllFavorites = { favoriteWrites++ },
            persistItems = { itemWrites++ },
            persistDeletion = { _, _ -> },
        )

        coordinator.updateItem(favorite.id, "favorite updated", "QR_CODE")
        coordinator.updateItem(history.id, "history updated", "EAN_13")
        coordinator.updateItem(999L, "missing", "CODE_128")

        val updatedItems = store.itemsSnapshot().associateBy { it.id }
        assertEquals("favorite updated", updatedItems.getValue(favorite.id).text)
        assertTrue(updatedItems.getValue(favorite.id).favorite)
        assertEquals("history updated", updatedItems.getValue(history.id).text)
        assertFalse(updatedItems.getValue(history.id).favorite)
        assertEquals(1, favoriteWrites)
        assertEquals(1, itemWrites)
    }
}
