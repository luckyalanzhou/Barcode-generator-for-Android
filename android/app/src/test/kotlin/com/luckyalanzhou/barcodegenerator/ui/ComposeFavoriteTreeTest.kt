package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesRowsProjectionCache
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeData
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeState
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.effectiveCollapsedFavoriteFolders
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.composeFavoriteRows
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.favoriteSearchExpandedPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

class ComposeFavoriteTreeTest {
    @Test
    fun foldersStayCollapsedBeforeTreeSyncFinishes() {
        val folderPaths = setOf("A", "A/Child", "B")

        val collapsed = effectiveCollapsedFavoriteFolders(
            treeState = FavoritesTreeState(),
            allFolderPaths = folderPaths,
            query = "",
            expandedSearchPaths = emptySet(),
        )

        assertEquals(folderPaths, collapsed)
    }

    @Test
    fun searchCanExpandMatchingFoldersBeforeTreeSyncFinishes() {
        val folderPaths = setOf("A", "A/Child", "B")

        val collapsed = effectiveCollapsedFavoriteFolders(
            treeState = FavoritesTreeState(),
            allFolderPaths = folderPaths,
            query = "match",
            expandedSearchPaths = setOf("A", "A/Child"),
        )

        assertEquals(setOf("B"), collapsed)
    }

    @Test
    fun indexedProjectionKeepsNestedFolderAndSearchResults() {
        val state = FavoritesTreeData(
            items = listOf(CodeItem(1, "alpha", "CODE_128", 1L, true, "a/b", false)),
            groups = listOf(FavoriteGroup(1, "a/b", "Alpha", 1L, mutableListOf(1))),
            folders = listOf("a", "a/b"),
        )

        val rows = composeFavoriteRows(state, "alpha", emptySet())

        assertEquals(listOf("a", "b", "Alpha"), rows.map { it.label })
        assertEquals(listOf(true, true, false), rows.map { it.folder })
    }

    @Test
    fun collapsedFolderHidesItsIndexedChildren() {
        val state = FavoritesTreeData(
            items = listOf(CodeItem(1, "alpha", "CODE_128", 1L, true, "a/b", false)),
            groups = listOf(FavoriteGroup(1, "a/b", "Alpha", 1L, mutableListOf(1))),
            folders = listOf("a", "a/b"),
        )

        val rows = composeFavoriteRows(state, "", setOf("a"))

        assertEquals(listOf("a"), rows.map { it.label })
    }

    @Test
    fun rootLevelFavoritesAreVisibleAndSearchableWithoutAFolder() {
        val group = FavoriteGroup(1, "", "Root favorite", 1L, mutableListOf(1))
        val state = FavoritesTreeData(
            items = listOf(CodeItem(1, "root-code", "CODE_128", 1L, true, "", false)),
            groups = listOf(group),
            folders = emptyList(),
        )

        val normalRows = composeFavoriteRows(state, "", emptySet())
        val searchRows = composeFavoriteRows(state, "root", emptySet())

        assertEquals(listOf("Root favorite"), normalRows.map { it.label })
        assertEquals(listOf(false), normalRows.map { it.folder })
        assertEquals(listOf("Root favorite"), searchRows.map { it.label })
        assertEquals(group, searchRows.single().group)
    }

    @Test
    fun projectionCacheHitsOnlyWhenInputIdentityQueryAndCollapseStateMatch() {
        val state = FavoritesTreeData(
            items = mutableListOf(CodeItem(1, "alpha", "CODE_128", 1L, true, "a", false)),
            groups = mutableListOf(),
            folders = mutableListOf("a"),
        )
        val cache = FavoritesRowsProjectionCache()
        val rows = composeFavoriteRows(state, "", emptySet())
        cache.store(state, "", emptySet(), rows)

        assertSame(rows, cache.find(state, "", emptySet()))
        assertNull(cache.find(state.copy(items = state.items.toMutableList()), "", emptySet()))
        assertNull(cache.find(state, "alpha", emptySet()))
        assertNull(cache.find(state, "", setOf("a")))
    }

    @Test
    fun searchMatchingDoesNotDependOnSystemLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val state = FavoritesTreeData(
                items = emptyList(),
                groups = listOf(FavoriteGroup(1, "ITEMS", "ITEM", 1L, mutableListOf())),
                folders = listOf("ITEMS"),
            )

            assertEquals(listOf("ITEMS", "ITEM"), composeFavoriteRows(state, "i", emptySet()).map { it.label })
            assertEquals(setOf("ITEMS"), favoriteSearchExpandedPaths(state, "i"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
