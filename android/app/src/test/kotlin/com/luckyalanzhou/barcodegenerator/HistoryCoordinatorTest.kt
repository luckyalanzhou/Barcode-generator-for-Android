package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import com.luckyalanzhou.barcodegenerator.presentation.history.HistoryCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCoordinatorTest {
    @Test
    fun deleteBatchOnlyClearsHistoryAndPersistsSnapshot() {
        val store = LibraryStateStore()
        val first = CodeItem(1, "one", "CODE_128", 1L, favorite = true, folder = "A", inHistory = true)
        val second = CodeItem(2, "two", "CODE_128", 2L, favorite = false, folder = "", inHistory = true)
        store.replace(listOf(first, second), emptyList(), emptyList())
        val persisted = mutableListOf<List<CodeItem>>()

        HistoryCoordinator(store) { persisted += it }.deleteBatch(listOf(first))

        assertTrue(store.itemsSnapshot().first { it.id == 1L }.inHistory.not())
        assertTrue(store.itemsSnapshot().first { it.id == 2L }.inHistory)
        assertEquals(listOf(false, true), persisted.single().map { it.inHistory })
        assertTrue(store.itemsSnapshot().first { it.id == 1L }.favorite)
    }

    @Test
    fun clearHistoryPersistsAllItems() {
        val store = LibraryStateStore()
        store.replace(
            listOf(
                CodeItem(1, "one", "CODE_128", 1L, false, "", true),
                CodeItem(2, "two", "CODE_128", 2L, false, "", true),
            ),
            emptyList(),
            emptyList(),
        )
        val persisted = mutableListOf<List<CodeItem>>()

        HistoryCoordinator(store) { persisted += it }.clearHistory()

        assertEquals(listOf(false, false), persisted.single().map { it.inHistory })
    }
}
