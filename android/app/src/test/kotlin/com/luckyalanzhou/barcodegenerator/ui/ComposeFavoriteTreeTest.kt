package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeFavoriteTreeTest {
    @Test
    fun indexedProjectionKeepsNestedFolderAndSearchResults() {
        val state = BarcodeDataState(
            items = listOf(CodeItem(1, "alpha", "CODE_128", 1L, true, "a/b", false)),
            groups = listOf(FavoriteGroup(1, "a/b", "Alpha", 1L, mutableListOf(1))),
            folders = listOf("a", "a/b"),
            isReady = true,
        )

        val rows = composeFavoriteRows(state, "alpha", emptySet())

        assertEquals(listOf("a", "b", "Alpha"), rows.map { it.label })
        assertEquals(listOf(true, true, false), rows.map { it.folder })
    }

    @Test
    fun collapsedFolderHidesItsIndexedChildren() {
        val state = BarcodeDataState(
            items = listOf(CodeItem(1, "alpha", "CODE_128", 1L, true, "a/b", false)),
            groups = listOf(FavoriteGroup(1, "a/b", "Alpha", 1L, mutableListOf(1))),
            folders = listOf("a", "a/b"),
            isReady = true,
        )

        val rows = composeFavoriteRows(state, "", setOf("a"))

        assertEquals(listOf("a"), rows.map { it.label })
    }
}
