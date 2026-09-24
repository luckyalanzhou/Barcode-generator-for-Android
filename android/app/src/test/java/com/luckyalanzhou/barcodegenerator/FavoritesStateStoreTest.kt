package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStateStoreTest {
    @Test
    fun unloadedGroupsAreNotMarkedAsAuthoritativeLinkSnapshots() {
        val store = LibraryStateStore()
        val group = FavoriteGroup(7L, "一级", "文件", 7L, mutableListOf())

        store.replace(emptyList(), listOf(group), listOf("一级"))
        assertEquals(emptySet<Long>(), store.loadedGroupLinkIdsSnapshot())

        store.markGroupLinksLoaded(group.id)
        assertEquals(setOf(group.id), store.loadedGroupLinkIdsSnapshot())
    }

    @Test
    fun clearingFavoritesAlsoClearsFolderHierarchyAndLoadedLinks() {
        val store = LibraryStateStore()
        val favorite = CodeItem(1L, "FAVORITE", "Code 128-B", favorite = true, folder = "一级/二级")
        val history = CodeItem(2L, "HISTORY", "Code 128-B", inHistory = true)
        val group = FavoriteGroup(7L, "一级/二级", "文件", 7L, mutableListOf(favorite.id))
        store.replace(listOf(favorite, history), listOf(group), listOf("一级", "一级/二级"))
        store.markGroupLinksLoaded(group.id)

        store.clearFavorites()

        assertEquals(emptyList<FavoriteGroup>(), store.groupsSnapshot())
        assertEquals(emptyList<String>(), store.foldersSnapshot())
        assertEquals(emptySet<Long>(), store.loadedGroupLinkIdsSnapshot())
        assertEquals(false, store.itemsSnapshot().first { it.id == favorite.id }.favorite)
        assertEquals("默认", store.itemsSnapshot().first { it.id == favorite.id }.folder)
        assertEquals(true, store.itemsSnapshot().first { it.id == history.id }.inHistory)
    }

    @Test
    fun concurrentSnapshotsAndEditsRemainConsistent() = runBlocking {
        val store = LibraryStateStore()
        val writers = (1L..8L).map { writer ->
            async(Dispatchers.Default) {
                repeat(100) { index ->
                    store.edit { items += CodeItem(writer * 1_000 + index, "内容", "Code 128-B") }
                    store.snapshot(isReady = true)
                }
            }
        }

        writers.awaitAll()

        assertEquals(800, store.itemsSnapshot().size)
        assertEquals(800, store.snapshot(true).items.size)
    }
}
