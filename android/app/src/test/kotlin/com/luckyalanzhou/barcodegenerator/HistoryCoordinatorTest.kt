package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import com.luckyalanzhou.barcodegenerator.presentation.history.HistoryCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCoordinatorTest {
    @Test
    fun deleteBatchClearsEveryMemberAndPersistsTheBatchIdentity() {
        val store = LibraryStateStore()
        val first = CodeItem(1, "one", "CODE_128", 1L, favorite = true, folder = "A", inHistory = true, historyBatchId = "batch")
        val second = CodeItem(2, "two", "CODE_128", 2L, favorite = false, folder = "", inHistory = true)
        val otherMember = CodeItem(3, "three", "CODE_128", 1L, historyBatchId = "batch")
        store.replace(listOf(first, second, otherMember), emptyList(), emptyList())
        val persisted = mutableListOf<Pair<String, Long>>()

        HistoryCoordinator(store) { batchId, createdAt -> persisted += batchId to createdAt }.deleteBatch(listOf(first))

        assertTrue(store.itemsSnapshot().first { it.id == 1L }.inHistory.not())
        assertTrue(store.itemsSnapshot().first { it.id == 2L }.inHistory)
        assertEquals(listOf("batch" to 1L), persisted)
        assertTrue(store.itemsSnapshot().first { it.id == 3L }.inHistory.not())
        assertTrue(store.itemsSnapshot().first { it.id == 1L }.favorite)
    }

    @Test
    fun clearHistoryClearsLoadedHistoryState() {
        val store = LibraryStateStore()
        store.replace(
            listOf(
                CodeItem(1, "one", "CODE_128", 1L, false, "", true),
                CodeItem(2, "two", "CODE_128", 2L, false, "", true),
            ),
            emptyList(),
            emptyList(),
        )
        HistoryCoordinator(store) { _, _ -> }.clearHistory()

        assertEquals(listOf(false, false), store.itemsSnapshot().map { it.inHistory })
    }
}
