package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class FavoriteGroupContentCoordinatorTest {
    @Test
    fun loadedContentUpdatesRegularAndSearchCaches() = runBlocking {
        val regularStore = storeWithGroup()
        val searchStore = storeWithGroup()
        var dataPublishCount = 0
        var searchPublishCount = 0
        val item = CodeItem(12L, "authoritative", "Code 128-B")
        val coordinator = FavoriteGroupContentCoordinator(
            loadContent = { groupId ->
                assertEquals(7L, groupId)
                FavoriteGroupContent(group(7L).copy(itemIds = mutableListOf(12L)), listOf(item), emptyList())
            },
            regularStore = regularStore,
            searchStore = searchStore,
            publishDataState = { dataPublishCount++ },
            publishSearchState = { searchPublishCount++ },
        )

        val loaded = coordinator.load(7L) as FavoriteGroupContentLoadResult.Loaded
        coordinator.cache(loaded)

        assertEquals(listOf(12L), regularStore.groupsSnapshot().single().itemIds)
        assertEquals(listOf(12L), searchStore.groupsSnapshot().single().itemIds)
        assertEquals(listOf(12L), regularStore.itemsSnapshot().map { it.id })
        assertEquals(listOf(12L), searchStore.itemsSnapshot().map { it.id })
        assertEquals(setOf(7L), regularStore.loadedGroupLinkIdsSnapshot())
        assertEquals(setOf(7L), searchStore.loadedGroupLinkIdsSnapshot())
        assertEquals(1, dataPublishCount)
        assertEquals(1, searchPublishCount)
        assertTrue(coordinator.isCurrent(7L, loaded.expectedSavedAt))
    }

    @Test
    fun invalidLinksAreRejectedWithoutUpdatingCaches() = runBlocking {
        val regularStore = storeWithGroup()
        val searchStore = storeWithGroup()
        val coordinator = coordinator(
            regularStore = regularStore,
            searchStore = searchStore,
            loadContent = { FavoriteGroupContent(group(7L), emptyList(), listOf(12L)) },
        )

        val result = coordinator.load(7L) as FavoriteGroupContentLoadResult.Rejected

        assertEquals(FavoriteGroupContentLoadFailure.INVALID_CONTENT, result.failure)
        assertTrue(regularStore.itemsSnapshot().isEmpty())
        assertTrue(searchStore.itemsSnapshot().isEmpty())
        assertTrue(regularStore.loadedGroupLinkIdsSnapshot().isEmpty())
    }

    @Test
    fun changedGroupIsDiscardedAfterRepositoryRead() = runBlocking {
        val regularStore = storeWithGroup()
        val searchStore = LibraryStateStore()
        val coordinator = coordinator(
            regularStore = regularStore,
            searchStore = searchStore,
            loadContent = {
                regularStore.edit { groups[0] = group(7L, savedAt = 8L) }
                FavoriteGroupContent(group(7L), listOf(CodeItem(12L, "stale", "Code 128-B")), emptyList())
            },
        )

        val result = coordinator.load(7L) as FavoriteGroupContentLoadResult.Rejected

        assertEquals(FavoriteGroupContentLoadFailure.GROUP_CHANGED, result.failure)
        assertTrue(regularStore.itemsSnapshot().isEmpty())
        assertTrue(searchStore.itemsSnapshot().isEmpty())
    }

    private fun coordinator(
        regularStore: LibraryStateStore,
        searchStore: LibraryStateStore,
        loadContent: suspend (Long) -> FavoriteGroupContent?,
    ) = FavoriteGroupContentCoordinator(
        loadContent = loadContent,
        regularStore = regularStore,
        searchStore = searchStore,
        publishDataState = {},
        publishSearchState = {},
    )

    private fun storeWithGroup(): LibraryStateStore = LibraryStateStore().also { store ->
        store.edit { groups += group(7L) }
    }

    private fun group(id: Long, savedAt: Long = 7L) =
        FavoriteGroup(id, "一级", "收藏$id", savedAt, mutableListOf(id))
}
