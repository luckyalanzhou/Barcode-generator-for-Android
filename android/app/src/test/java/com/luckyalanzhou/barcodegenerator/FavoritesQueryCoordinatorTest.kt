package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupContent
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import com.luckyalanzhou.barcodegenerator.domain.FavoriteSearchGroupCursor
import com.luckyalanzhou.barcodegenerator.domain.FavoriteSearchItemCursor
import com.luckyalanzhou.barcodegenerator.domain.LegacyBarcodeData
import com.luckyalanzhou.barcodegenerator.domain.StartupBarcodeSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesQueryCoordinatorTest {
    @Test
    fun paginationUsesStableCursorAfterLoadedGroupIsDeleted() = runBlocking {
        val firstPage = (1L..100L).map { group(it) }
        val secondPage = (101L..101L).map { group(it) }
        val repository = FakeFavoriteRepository(
            firstPage = firstPage,
            secondPage = secondPage,
        )
        val store = FavoritesStateStore()
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
    fun searchAddsOnlyMissingGroupsAndItems() = runBlocking {
        val store = FavoritesStateStore()
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
        val store = FavoritesStateStore()
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
        val store = FavoritesStateStore()
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
        val regularStore = FavoritesStateStore()
        regularStore.edit { groups += group(99L, "普通收藏") }
        val searchStore = FavoritesStateStore()
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
}

private class FakeFavoriteRepository(
    private val groups: List<FavoriteGroup> = emptyList(),
    private val items: List<CodeItem> = emptyList(),
    private val firstPage: List<FavoriteGroup> = groups,
    private val secondPage: List<FavoriteGroup> = emptyList(),
) : BarcodeRepository {
    val requestedCursors = mutableListOf<FavoriteGroupPageCursor?>()

    override suspend fun loadFavoriteGroupPage(limit: Int, cursor: FavoriteGroupPageCursor?): List<FavoriteGroup> {
        requestedCursors += cursor
        return when (cursor) {
            FavoriteGroupPageCursor(100L, 100L) -> secondPage
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
    override suspend fun upsertItems(items: List<CodeItem>) = Unit
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
    override suspend fun loadFavoriteGroupContent(groupId: Long): FavoriteGroupContent? = null
    override suspend fun loadFolders() = emptyList<String>()
    override suspend fun loadSnapshot() = BarcodeSnapshot(emptyList(), emptyList(), emptyList(), emptyList())
    override suspend fun loadStartupSnapshot() = StartupBarcodeSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), false)
    override suspend fun appendSnapshot(snapshot: BarcodeSnapshot) = Unit
    override suspend fun commitFavoriteImport(snapshot: BarcodeSnapshot, replacedGroupIds: Set<Long>) = Unit
    override suspend fun migrateLegacyDataIfNeeded(legacy: LegacyBarcodeData) = Unit
}
