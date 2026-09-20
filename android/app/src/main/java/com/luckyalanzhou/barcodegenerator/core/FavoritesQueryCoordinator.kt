package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val FAVORITE_GROUP_PAGE_SIZE = 100
private const val FAVORITE_SEARCH_PAGE_SIZE = 50

/** 收藏列表的查询边界：分页、搜索和延迟加载，不处理收藏变更。 */
internal class FavoritesQueryCoordinator(
    private val repository: BarcodeRepository,
    private val store: FavoritesStateStore,
) {
    private var cursor: FavoriteGroupPageCursor? = null
    private var hasMore = false
    private var loadingMore = false
    private var activeSearch = ""
    private var searchGroupOffset = 0
    private var searchItemOffset = 0
    private var searchGroupsHasMore = false
    private var searchItemsHasMore = false

    fun resetPaging(initialCursor: FavoriteGroupPageCursor?, initialHasMore: Boolean) {
        cursor = initialCursor
        hasMore = initialHasMore
        loadingMore = false
        activeSearch = ""
        searchGroupOffset = 0
        searchItemOffset = 0
        searchGroupsHasMore = false
        searchItemsHasMore = false
    }

    /** Keeps the next-page cursor aligned after an in-memory favorite mutation. */
    fun onMutation() {
        cursor = store.groupsSnapshot().lastOrNull()?.let { FavoriteGroupPageCursor(it.savedAt, it.id) }
        loadingMore = false
    }

    suspend fun search(query: String) {
        if (query.isBlank()) {
            activeSearch = ""
            searchGroupOffset = 0
            searchItemOffset = 0
            searchGroupsHasMore = false
            searchItemsHasMore = false
            return
        }
        delay(250)
        if (activeSearch != query) {
            activeSearch = query
            searchGroupOffset = 0
            searchItemOffset = 0
            searchGroupsHasMore = true
            searchItemsHasMore = true
        }
        loadSearchPage(query)
    }

    suspend fun loadMore(query: String = ""): Boolean {
        if (query.isNotBlank()) return loadMoreSearch(query)
        if (!hasMore || loadingMore) return false
        loadingMore = true
        return try {
            val page = withContext(Dispatchers.IO) {
                repository.loadFavoriteGroupPage(FAVORITE_GROUP_PAGE_SIZE, cursor)
            }
            val knownIds = store.groupsSnapshot().mapTo(HashSet()) { it.id }
            store.edit { groups.addAll(page.filterNot { it.id in knownIds }) }
            page.lastOrNull()?.let { cursor = FavoriteGroupPageCursor(it.savedAt, it.id) }
            hasMore = page.size == FAVORITE_GROUP_PAGE_SIZE
            true
        } finally {
            loadingMore = false
        }
    }

    private suspend fun loadMoreSearch(query: String): Boolean {
        if (query != activeSearch || loadingMore || (!searchGroupsHasMore && !searchItemsHasMore)) return false
        return loadSearchPage(query)
    }

    private suspend fun loadSearchPage(query: String): Boolean {
        if (loadingMore) return false
        loadingMore = true
        return try {
            val groupPage = if (searchGroupsHasMore) withContext(Dispatchers.IO) {
                repository.searchFavoriteGroups(query, FAVORITE_SEARCH_PAGE_SIZE, searchGroupOffset)
            } else emptyList()
            val itemPage = if (searchItemsHasMore) withContext(Dispatchers.IO) {
                repository.searchFavoriteItems(query, FAVORITE_SEARCH_PAGE_SIZE, searchItemOffset)
            } else emptyList()
            val knownGroupIds = store.groupsSnapshot().mapTo(HashSet()) { it.id }
            val knownItemIds = store.itemsSnapshot().mapTo(HashSet()) { it.id }
            store.edit {
                groups.addAll(groupPage.filterNot { it.id in knownGroupIds })
                items.addAll(itemPage.filterNot { it.id in knownItemIds })
            }
            searchGroupOffset += groupPage.size
            searchItemOffset += itemPage.size
            searchGroupsHasMore = groupPage.size == FAVORITE_SEARCH_PAGE_SIZE
            searchItemsHasMore = itemPage.size == FAVORITE_SEARCH_PAGE_SIZE
            groupPage.isNotEmpty() || itemPage.isNotEmpty()
        } finally {
            loadingMore = false
        }
    }
}
