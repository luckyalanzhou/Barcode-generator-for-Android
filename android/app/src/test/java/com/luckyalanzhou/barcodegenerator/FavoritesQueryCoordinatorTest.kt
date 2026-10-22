package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupContent
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import com.luckyalanzhou.barcodegenerator.domain.FavoriteSearchGroupCursor
import com.luckyalanzhou.barcodegenerator.domain.FavoriteSearchItemCursor
import com.luckyalanzhou.barcodegenerator.domain.LegacyBarcodeData
import com.luckyalanzhou.barcodegenerator.domain.StartupBarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesMutationCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesQuerySession
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesQueryCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesQueryCoordinatorTest {
    @Test
    fun querySessionUsesTheSharedSearchStore() = runBlocking {
        val dataSession = LibraryDataSession()
        val repository = FakeFavoriteRepository(groups = listOf(group(1L, "搜索结果")))
        val querySession = FavoritesQuerySession(
            repository = repository,
            dataSession = dataSession,
            persistence = persistence(repository),
        )

        querySession.search("搜索")

        assertEquals(listOf(1L), querySession.searchState.value.groups.map { it.id })
        assertTrue(dataSession.dataState.value.groups.isEmpty())
    }

    @Test
    fun mutationRefreshWaitsForQueuedWriteAndReloadsSearchProjection() = runBlocking {
        val repository = FakeFavoriteRepository(groups = listOf(group(1L, "收藏旧名")))
        val dataSession = LibraryDataSession()
        val persistence = persistence(repository)
        val querySession = FavoritesQuerySession(repository, dataSession, persistence)

        querySession.search("收藏")
        assertEquals("收藏旧名", querySession.searchState.value.groups.single().name)

        persistence.updateFavoriteGroupMetadata(1L, "收藏新名", "一级", 2L)
        querySession.onLibraryMutation()
        querySession.refreshSearchAfterMutation()

        assertEquals("收藏新名", querySession.searchState.value.groups.single().name)
    }

    @Test
    fun libraryMutationInvalidatesGroupContentReadInFlight() = runBlocking {
        val group = group(1L)
        val enteredRead = CompletableDeferred<Unit>()
        val continueRead = CompletableDeferred<FavoriteGroupContent?>()
        val repository = FakeFavoriteRepository(
            groups = listOf(group),
            beforeLoadGroupContent = {
                enteredRead.complete(Unit)
                continueRead.await()
            },
        )
        val dataSession = LibraryDataSession().apply {
            store.edit { groups += group }
        }
        val querySession = FavoritesQuerySession(repository, dataSession, persistence(repository))
        val load = async { querySession.loadGroupContent(group.id) }

        enteredRead.await()
        querySession.invalidateGroupContentLoads()
        continueRead.complete(FavoriteGroupContent(group, listOf(CodeItem(12L, "stale", "Code 128-B")), emptyList()))

        val result = load.await() as com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoriteGroupContentLoadResult.Rejected
        assertEquals(
            com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoriteGroupContentLoadFailure.GROUP_CHANGED,
            result.failure,
        )
        assertTrue(dataSession.store.itemsSnapshot().isEmpty())
    }

    @Test
    fun paginationUsesStableCursorAfterLoadedGroupIsDeleted() = runBlocking {
        val firstPage = (1L..100L).map { group(it) }
        val secondPage = (101L..101L).map { group(it) }
        val repository = FakeFavoriteRepository(
            firstPage = firstPage,
            secondPage = secondPage,
        )
        val store = LibraryStateStore()
        store.edit { groups.addAll(firstPage) }
        val coordinator = FavoritesQueryCoordinator(repository, store)
        coordinator.resetPaging(FavoriteGroupPageCursor(firstPage.last().savedAt, firstPage.last().id), true)

        store.edit { groups.removeAt(0) }
        coordinator.onMutation()
        assertTrue(coordinator.loadMore())

        assertEquals(100, store.groupsSnapshot().size)
        assertEquals(101L, store.groupsSnapshot().last().id)
        assertEquals(FavoriteGroupPageCursor(100L, 100L), repository.requestedCursors.single())
    }

    @Test
    fun paginationCursorStaysAtOldestLoadedGroupAfterRenameMovesItToFront() = runBlocking {
        val firstPage = (100L downTo 1L).map(::group)
        val secondPage = listOf(group(0L))
        val repository = FakeFavoriteRepository(
            groups = firstPage + secondPage,
            firstPage = firstPage,
            secondPage = secondPage,
            secondPageCursor = FavoriteGroupPageCursor(2L, 2L),
        )
        val store = LibraryStateStore().apply { replace(emptyList(), firstPage, listOf("一级")) }
        val persistence = persistence(repository)
        val mutations = FavoritesMutationCoordinator(store, persistence)
        val coordinator = FavoritesQueryCoordinator(repository, store)
        coordinator.resetPaging(FavoriteGroupPageCursor(1L, 1L), initialHasMore = true)

        assertTrue(mutations.renameGroupAndPersist(1L, "已重命名"))
        coordinator.onMutation()
        assertTrue(coordinator.loadMore())
        persistence.awaitPendingWrites()

        assertEquals(FavoriteGroupPageCursor(2L, 2L), repository.requestedCursors.single())
        assertEquals(101, store.groupsSnapshot().size)
        assertEquals(0L, store.groupsSnapshot().last().id)
    }

    @Test
    fun regularPageFinishingAfterMutationCannotPolluteUpdatedSnapshot() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        val initial = group(1L)
        val stalePage = group(2L)
        val repository = FakeFavoriteRepository(
            firstPage = listOf(stalePage),
            beforeLoadPage = {
                requestStarted.complete(Unit)
                releaseRequest.await()
            },
        )
        val store = LibraryStateStore().apply { edit { groups.add(initial) } }
        val coordinator = FavoritesQueryCoordinator(repository, store)
        coordinator.resetPaging(FavoriteGroupPageCursor(initial.savedAt, initial.id), true)

        val request = async { coordinator.loadMore() }
        requestStarted.await()
        store.edit { groups += group(3L, "变更后的快照") }
        coordinator.onMutation()
        releaseRequest.complete(Unit)

        assertFalse(request.await())
        assertEquals(listOf(1L, 3L), store.groupsSnapshot().map { it.id })
    }

    @Test
    fun searchAddsOnlyMissingGroupsAndItems() = runBlocking {
        val store = LibraryStateStore()
        store.edit {
            groups.add(group(1L, "命中"))
            items.add(CodeItem(1L, "旧内容", "Code 128-B"))
        }
        val repository = FakeFavoriteRepository(
            groups = listOf(group(2L, "搜索结果")),
            items = listOf(CodeItem(2L, "搜索内容", "Code 128-B")),
        )
        val coordinator = FavoritesQueryCoordinator(repository, store)

        coordinator.search("搜索")

        assertEquals(listOf(1L, 2L), store.groupsSnapshot().map { it.id })
        assertEquals(listOf(1L, 2L), store.itemsSnapshot().map { it.id })
    }

    @Test
    fun contentSearchKeepsMatchedGroupItemRelations() = runBlocking {
        val store = LibraryStateStore()
        val repository = FakeFavoriteRepository(
            groups = listOf(group(2L, "不靠名称命中").also { it.itemIds += 2L }),
            items = listOf(CodeItem(2L, "搜索内容", "Code 128-B")),
        )
        val coordinator = FavoritesQueryCoordinator(repository, store)

        coordinator.search("搜索内容")

        assertEquals(listOf(2L), store.groupsSnapshot().single().itemIds)
    }

    @Test
    fun searchLoadsTheNextPageWithAnOffset() = runBlocking {
        val repository = FakeFavoriteRepository(
            groups = (1L..101L).map { group(it, "搜索结果$it") },
        )
        val store = LibraryStateStore()
        val coordinator = FavoritesQueryCoordinator(repository, store)

        coordinator.search("搜索")

        assertEquals(50, store.groupsSnapshot().size)
        assertEquals(1L, store.groupsSnapshot().first().id)
        assertEquals(50L, store.groupsSnapshot().last().id)

        assertTrue(coordinator.loadMore("搜索"))

        assertEquals(100, store.groupsSnapshot().size)
        assertEquals(51L, store.groupsSnapshot()[50].id)
        assertEquals(100L, store.groupsSnapshot().last().id)
    }

    @Test
    fun searchResultsDoNotPolluteRegularFavoriteStore() = runBlocking {
        val regularStore = LibraryStateStore()
        regularStore.edit { groups += group(99L, "普通收藏") }
        val searchStore = LibraryStateStore()
        val repository = FakeFavoriteRepository(
            groups = listOf(group(1L, "搜索结果")),
        )
        val coordinator = FavoritesQueryCoordinator(repository, regularStore, searchStore)

        coordinator.search("搜索")

        assertEquals(listOf(99L), regularStore.groupsSnapshot().map { it.id })
        assertEquals(listOf(1L), searchStore.groupsSnapshot().map { it.id })

        coordinator.search("")

        assertEquals(listOf(99L), regularStore.groupsSnapshot().map { it.id })
        assertTrue(searchStore.groupsSnapshot().isEmpty())
    }

    private fun group(id: Long, name: String = "收藏$id") =
        FavoriteGroup(id, "一级", name, id, mutableListOf())

    private fun persistence(repository: BarcodeRepository) = BarcodePersistenceCoordinator(
        repository,
        object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        },
    )
}

private class FakeFavoriteRepository(
    var groups: List<FavoriteGroup> = emptyList(),
    private val items: List<CodeItem> = emptyList(),
    private val firstPage: List<FavoriteGroup> = groups,
    private val secondPage: List<FavoriteGroup> = emptyList(),
    private val secondPageCursor: FavoriteGroupPageCursor = FavoriteGroupPageCursor(100L, 100L),
    private val beforeLoadPage: suspend () -> Unit = {},
    private val beforeLoadGroupContent: suspend (Long) -> FavoriteGroupContent? = { null },
) : BarcodeRepository {
    val requestedCursors = mutableListOf<FavoriteGroupPageCursor?>()

    override suspend fun loadFavoriteGroupPage(limit: Int, cursor: FavoriteGroupPageCursor?): List<FavoriteGroup> {
        beforeLoadPage()
        requestedCursors += cursor
        return when (cursor) {
            secondPageCursor -> secondPage
            else -> firstPage.take(limit)
        }
    }

    override suspend fun searchFavoriteGroups(query: String, limit: Int, cursor: FavoriteSearchGroupCursor?) =
        groups.drop(cursor?.let { value -> groups.indexOfFirst { it.savedAt == value.savedAt && it.id == value.id } + 1 } ?: 0).take(limit)
    override suspend fun loadFavoriteGroupsByIds(ids: List<Long>) = groups.filter { it.id in ids }
    override suspend fun searchFavoriteItems(query: String, limit: Int, cursor: FavoriteSearchItemCursor?) =
        items.drop(cursor?.let { value -> items.indexOfFirst { it.createdAt == value.createdAt && it.id == value.id } + 1 } ?: 0).take(limit)

    override suspend fun saveAll(snapshot: BarcodeSnapshot) = Unit
    override suspend fun applyFavoritesMutation(snapshot: BarcodeSnapshot) = Unit
    override suspend fun saveItems(items: List<CodeItem>) = Unit
    override suspend fun clearHistory() = Unit
    override suspend fun upsertItems(items: List<CodeItem>) = Unit
    override suspend fun insertGeneratedItems(items: List<CodeItem>) = error("Generation is not used by these query tests")
    override suspend fun deleteItem(itemId: Long, modifiedAt: Long) = Unit
    override suspend fun updateFavoriteGroupMetadata(groupId: Long, name: String, folder: String, savedAt: Long) {
        groups = groups.map { group ->
            if (group.id == groupId) group.copy(name = name, folder = folder, savedAt = savedAt) else group
        }
    }
    override suspend fun loadItemsByIds(ids: List<Long>) = emptyList<CodeItem>()
    override suspend fun clearFavoriteFlags(ids: List<Long>) = Unit
    override suspend fun clearFavoriteFlagsForGroups(groupIds: List<Long>) = Unit
    override suspend fun clearAllFavoriteFlags() = Unit
    override suspend fun saveFavoriteGroups(groups: List<FavoriteGroup>, links: List<FavoriteGroupItem>) = Unit
    override suspend fun deleteFavoriteGroups(ids: List<Long>) = Unit
    override suspend fun clearAllFavoriteGroups() = Unit
    override suspend fun renameFavoriteFolder(path: String, renamedPath: String) = Unit
    override suspend fun deleteFavoriteFolder(path: String) = Unit
    override suspend fun saveFavoriteFolders(folders: List<String>) = Unit
    override suspend fun loadItems() = emptyList<CodeItem>()
    override suspend fun loadGroups() = emptyList<FavoriteGroup>()
    override suspend fun loadGroupItems() = emptyList<FavoriteGroupItem>()
    override suspend fun loadGroupItemIds(groupId: Long) = emptyList<Long>()
    override suspend fun loadFavoriteGroupContent(groupId: Long): FavoriteGroupContent? = beforeLoadGroupContent(groupId)
    override suspend fun loadFolders() = emptyList<String>()
    override suspend fun loadSnapshot() = BarcodeSnapshot(emptyList(), emptyList(), emptyList(), emptyList())
    override suspend fun loadStartupSnapshot() = StartupBarcodeSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), false)
    override suspend fun appendSnapshot(snapshot: BarcodeSnapshot) = Unit
    override suspend fun commitFavoriteImport(snapshot: BarcodeSnapshot, replacedGroupIds: Set<Long>) = Unit
    override suspend fun migrateLegacyDataIfNeeded(legacy: LegacyBarcodeData) = true
}
