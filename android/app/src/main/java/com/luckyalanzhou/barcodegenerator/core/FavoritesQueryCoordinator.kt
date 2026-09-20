package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val FAVORITE_GROUP_PAGE_SIZE = 100

/** 收藏列表的查询边界：分页、搜索和延迟加载，不处理收藏变更。 */
internal class FavoritesQueryCoordinator(
    private val repository: BarcodeRepository,
    private val store: FavoritesStateStore,
) {
    private var cursor: FavoriteGroupPageCursor? = null
    private var hasMore = false
    private var loadingMore = false

    fun resetPaging(initialCursor: FavoriteGroupPageCursor?, initialHasMore: Boolean) {
        cursor = initialCursor
        hasMore = initialHasMore
        loadingMore = false
    }

    /** Keeps the next-page cursor aligned after an in-memory favorite mutation. */
    fun onMutation() {
        cursor = store.groupsSnapshot().lastOrNull()?.let { FavoriteGroupPageCursor(it.savedAt, it.id) }
        loadingMore = false
    }

    suspend fun search(query: String) {
        if (query.isBlank()) return
        delay(250)
        val matchingGroupIds = withContext(Dispatchers.IO) { repository.searchFavoriteGroupIds(query) }
        val knownGroupIds = store.groupsSnapshot().mapTo(HashSet()) { it.id }
        if (matchingGroupIds.isNotEmpty()) {
            val missingGroups = withContext(Dispatchers.IO) {
                repository.loadFavoriteGroupsByIds(matchingGroupIds.filterNot { it in knownGroupIds })
            }
            store.edit { groups.addAll(missingGroups) }
        }
        val matches = withContext(Dispatchers.IO) { repository.searchFavoriteItems(query) }
        store.edit {
            val knownIds = items.mapTo(HashSet()) { it.id }
            items.addAll(matches.filterNot { it.id in knownIds })
        }
    }

    suspend fun loadMore(): Boolean {
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
}
