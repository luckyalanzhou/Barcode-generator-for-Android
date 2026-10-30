package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import com.luckyalanzhou.barcodegenerator.domain.FavoriteSearchGroupCursor
import com.luckyalanzhou.barcodegenerator.domain.FavoriteSearchItemCursor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val FAVORITE_GROUP_PAGE_SIZE = 100
private const val FAVORITE_SEARCH_PAGE_SIZE = 50

/** 收藏列表的查询边界：分页、搜索和延迟加载，不处理收藏变更。 */
internal class FavoritesQueryCoordinator(
    private val repository: BarcodeRepository,
    private val store: LibraryStateStore,
    private val searchStore: LibraryStateStore = store,
) {
    private val regularStateLock = Any()
    private var cursor: FavoriteGroupPageCursor? = null
    private var hasMore = false
    private var loadingRegularPage = false
    private var regularGeneration = 0L
    private val searchMutex = Mutex()
    private var activeSearch = ""
    private var searchGroupCursor: FavoriteSearchGroupCursor? = null
    private var searchItemCursor: FavoriteSearchItemCursor? = null
    private var searchGroupsHasMore = false
    private var searchItemsHasMore = false

    suspend fun resetPaging(initialCursor: FavoriteGroupPageCursor?, initialHasMore: Boolean) {
        synchronized(regularStateLock) {
            cursor = initialCursor
            hasMore = initialHasMore
            loadingRegularPage = false
            regularGeneration++
        }
        searchMutex.withLock {
            activeSearch = ""
            searchGroupCursor = null
            searchItemCursor = null
            searchGroupsHasMore = false
            searchItemsHasMore = false
            clearSearchStore()
        }
    }

    fun searchSnapshot(isReady: Boolean): BarcodeDataState = searchStore.snapshot(isReady)

    private fun clearSearchStore() {
        if (searchStore !== store) {
            searchStore.replace(emptyList(), emptyList(), emptyList())
        }
    }

    /** 内存中的收藏数据变更后，同步更新下一页游标。 */
    fun onMutation() {
        val latestCursor = store.groupsSnapshot().lastOrNull()?.let { FavoriteGroupPageCursor(it.savedAt, it.id) }
        synchronized(regularStateLock) {
            regularGeneration++
            cursor = latestCursor
            loadingRegularPage = false
        }
    }

    /** 等待排队写入提交后，重新加载当前搜索结果视图。 */
    suspend fun refreshActiveSearch(): Boolean {
        return searchMutex.withLock {
            val query = activeSearch
            if (query.isBlank()) return@withLock false

            searchGroupCursor = null
            searchItemCursor = null
            searchGroupsHasMore = true
            searchItemsHasMore = true
            clearSearchStore()
            loadSearchPage(query)
            true
        }
    }

    suspend fun search(query: String) {
        if (query.isBlank()) {
            searchMutex.withLock {
                activeSearch = ""
                searchGroupCursor = null
                searchItemCursor = null
                searchGroupsHasMore = false
                searchItemsHasMore = false
                clearSearchStore()
            }
            return
        }
        delay(250)
        searchMutex.withLock {
            if (activeSearch != query) {
                activeSearch = query
                searchGroupCursor = null
                searchItemCursor = null
                searchGroupsHasMore = true
                searchItemsHasMore = true
                clearSearchStore()
            }
            loadSearchPage(query)
        }
    }

    suspend fun loadMore(query: String = ""): Boolean {
        if (query.isNotBlank()) return loadMoreSearch(query)
        val request = synchronized(regularStateLock) {
            if (!hasMore || loadingRegularPage) return false
            loadingRegularPage = true
            cursor to regularGeneration
        }
        return try {
            val page = withContext(Dispatchers.IO) {
                repository.loadFavoriteGroupPage(FAVORITE_GROUP_PAGE_SIZE, request.first)
            }
            synchronized(regularStateLock) {
                if (request.second != regularGeneration) return@synchronized false
                store.edit {
                    val knownIds = groups.mapTo(HashSet()) { it.id }
                    groups.addAll(page.filterNot { it.id in knownIds })
                }
                page.lastOrNull()?.let { cursor = FavoriteGroupPageCursor(it.savedAt, it.id) }
                hasMore = page.size == FAVORITE_GROUP_PAGE_SIZE
                loadingRegularPage = false
                true
            }
        } finally {
            synchronized(regularStateLock) {
                if (request.second == regularGeneration) loadingRegularPage = false
            }
        }
    }

    private suspend fun loadMoreSearch(query: String): Boolean {
        return searchMutex.withLock {
            if (query != activeSearch || (!searchGroupsHasMore && !searchItemsHasMore)) return@withLock false
            loadSearchPage(query)
        }
    }

    private suspend fun loadSearchPage(query: String): Boolean {
        val groupPage = if (searchGroupsHasMore) withContext(Dispatchers.IO) {
            repository.searchFavoriteGroups(query, FAVORITE_SEARCH_PAGE_SIZE, searchGroupCursor)
        } else emptyList()
        val itemPage = if (searchItemsHasMore) withContext(Dispatchers.IO) {
            repository.searchFavoriteItems(query, FAVORITE_SEARCH_PAGE_SIZE, searchItemCursor)
        } else emptyList()
        val knownGroupIds = searchStore.groupsSnapshot().mapTo(HashSet()) { it.id }
        val knownItemIds = searchStore.itemsSnapshot().mapTo(HashSet()) { it.id }
        searchStore.edit {
            groups.addAll(groupPage.filterNot { it.id in knownGroupIds })
            items.addAll(itemPage.filterNot { it.id in knownItemIds })
        }
        groupPage.lastOrNull()?.let { searchGroupCursor = FavoriteSearchGroupCursor(it.savedAt, it.id) }
        itemPage.lastOrNull()?.let { searchItemCursor = FavoriteSearchItemCursor(it.createdAt, it.id) }
        searchGroupsHasMore = groupPage.size == FAVORITE_SEARCH_PAGE_SIZE
        searchItemsHasMore = itemPage.size == FAVORITE_SEARCH_PAGE_SIZE
        return groupPage.isNotEmpty() || itemPage.isNotEmpty()
    }
}
