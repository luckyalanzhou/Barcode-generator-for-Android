package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import com.luckyalanzhou.barcodegenerator.domain.LegacyBarcodeData
import com.luckyalanzhou.barcodegenerator.domain.StartupBarcodeSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesQueryCoordinatorTest {
    @Test
    fun paginationLoadsNextPageAfterLoadedGroupIsDeleted() = runBlocking {
        val firstPage = (1L..100L).map { group(it) }
        val secondPage = (101L..101L).map { group(it) }
        val repository = FakeFavoriteRepository(
            firstPage = firstPage,
            secondPage = secondPage,
        )
        val store = FavoritesStateStore()
        store.groups.addAll(firstPage)
        val coordinator = FavoritesQueryCoordinator(repository, store)
        coordinator.resetPaging(store.groups.size, true)

        store.groups.removeAt(0)
        coordinator.onMutation()
        assertTrue(coordinator.loadMore())

        assertEquals(100, store.groups.size)
        assertEquals(101L, store.groups.last().id)
        assertEquals(99, repository.requestedOffsets.single())
    }

    @Test
    fun searchAddsOnlyMissingGroupsAndItems() = runBlocking {
        val store = FavoritesStateStore()
        store.groups.add(group(1L, "命中"))
        store.items.add(CodeItem(1L, "旧内容", "Code 128-B"))
        val repository = FakeFavoriteRepository(
            groups = listOf(group(2L, "搜索结果")),
            items = listOf(CodeItem(2L, "搜索内容", "Code 128-B")),
        )
        val coordinator = FavoritesQueryCoordinator(repository, store)

        coordinator.search("搜索")

        assertEquals(listOf(1L, 2L), store.groups.map { it.id })
        assertEquals(listOf(1L, 2L), store.items.map { it.id })
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
    val requestedOffsets = mutableListOf<Int>()

    override suspend fun loadFavoriteGroupPage(limit: Int, offset: Int): List<FavoriteGroup> {
        requestedOffsets += offset
        return when (offset) {
            99 -> secondPage
            else -> firstPage.take(limit)
        }
    }

    override suspend fun searchFavoriteGroupIds(query: String) = groups.map { it.id }
    override suspend fun loadFavoriteGroupsByIds(ids: List<Long>) = groups.filter { it.id in ids }
    override suspend fun searchFavoriteItems(query: String) = items

    override suspend fun saveAll(snapshot: BarcodeSnapshot) = Unit
    override suspend fun saveItems(items: List<CodeItem>) = Unit
    override suspend fun upsertItems(items: List<CodeItem>) = Unit
    override suspend fun loadItemsByIds(ids: List<Long>) = emptyList<CodeItem>()
    override suspend fun clearFavoriteFlags(ids: List<Long>) = Unit
    override suspend fun clearFavoriteFlagsForGroups(groupIds: List<Long>) = Unit
    override suspend fun clearAllFavoriteFlags() = Unit
    override suspend fun saveFavoriteGroups(groups: List<FavoriteGroup>, links: List<FavoriteGroupItem>) = Unit
    override suspend fun saveFavoriteGroupMetadata(groups: List<FavoriteGroup>) = Unit
    override suspend fun saveFavoriteGroupLinks(groups: List<FavoriteGroup>) = Unit
    override suspend fun deleteFavoriteGroups(ids: List<Long>) = Unit
    override suspend fun clearAllFavoriteGroups() = Unit
    override suspend fun renameFavoriteFolder(path: String, renamedPath: String) = Unit
    override suspend fun deleteFavoriteFolder(path: String) = Unit
    override suspend fun saveFavoriteFolders(folders: List<String>) = Unit
    override suspend fun loadItems() = emptyList<CodeItem>()
    override suspend fun loadGroups() = emptyList<FavoriteGroup>()
    override suspend fun loadGroupItems() = emptyList<FavoriteGroupItem>()
    override suspend fun loadGroupItemIds(groupId: Long) = emptyList<Long>()
    override suspend fun loadFolders() = emptyList<String>()
    override suspend fun loadSnapshot() = BarcodeSnapshot(emptyList(), emptyList(), emptyList(), emptyList())
    override suspend fun loadStartupSnapshot() = StartupBarcodeSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), false)
    override suspend fun appendSnapshot(snapshot: BarcodeSnapshot) = Unit
    override suspend fun migrateLegacyDataIfNeeded(legacy: LegacyBarcodeData) = Unit
}
