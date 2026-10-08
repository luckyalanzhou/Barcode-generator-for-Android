package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import com.luckyalanzhou.barcodegenerator.domain.FavoriteSearchGroupCursor
import com.luckyalanzhou.barcodegenerator.domain.FavoriteSearchItemCursor
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
    private var cursor: FavoriteGroupPageCursor? = null
    private var hasMore = false
    private var loadingRegularPage = false
    private var loadingSearchPage = false
    private var activeSearch = ""
    private var searchGroupCursor: FavoriteSearchGroupCursor? = null
    private var searchItemCursor: FavoriteSearchItemCursor? = null
    private var searchGroupsHasMore = false
    private var searchItemsHasMore = false

    fun resetPaging(initialCursor: FavoriteGroupPageCursor?, initialHasMore: Boolean) {
        cursor = initialCursor
        hasMore = initialHasMore
        loadingRegularPage = false
        loadingSearchPage = false
        activeSearch = ""
        searchGroupCursor = null
        searchItemCursor = null
        searchGroupsHasMore = false
        searchItemsHasMore = false
        clearSearchStore()
    }

    /** Clears only the query result; the paged regular favorite list remains intact. */
    fun clearSearch() {
        activeSearch = ""
        searchGroupCursor = null
        searchItemCursor = null
        searchGroupsHasMore = false
        searchItemsHasMore = false
        clearSearchStore()
    }

    fun searchSnapshot(isReady: Boolean): BarcodeDataState = searchStore.snapshot(isReady)

    private fun clearSearchStore() {
        if (searchStore !== store) {
            searchStore.replace(emptyList(), emptyList(), emptyList())
        }
    }

    /** Keeps the next-page cursor aligned after an in-memory favorite mutation. */
    fun onMutation() {
        cursor = store.groupsSnapshot().lastOrNull()?.let { FavoriteGroupPageCursor(it.savedAt, it.id) }
        loadingRegularPage = false
        loadingSearchPage = false
    }

    suspend fun search(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        delay(250)
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

    suspend fun loadMore(query: String = ""): Boolean {
        if (query.isNotBlank()) return loadMoreSearch(query)
        if (!hasMore || loadingRegularPage) return false
        loadingRegularPage = true
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
            loadingRegularPage = false
        }
    }

    private suspend fun loadMoreSearch(query: String): Boolean {
        if (query != activeSearch || loadingSearchPage || (!searchGroupsHasMore && !searchItemsHasMore)) return false
        return loadSearchPage(query)
    }

    private suspend fun loadSearchPage(query: String): Boolean {
        if (loadingSearchPage) return false
        loadingSearchPage = true
        return try {
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
            groupPage.isNotEmpty() || itemPage.isNotEmpty()
        } finally {
            loadingSearchPage = false
        }
    }
}
