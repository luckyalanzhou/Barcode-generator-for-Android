package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val FAVORITE_GROUP_PAGE_SIZE = 100

/** 收藏列表的查询边界：分页、搜索和延迟加载，不处理收藏变更。 */
class FavoritesQueryCoordinator(
    private val repository: BarcodeRepository,
    private val items: MutableList<CodeItem>,
    private val groups: MutableList<FavoriteGroup>,
) {
    private var offset = 0
    private var hasMore = false
    private var loadingMore = false

    fun resetPaging(initialOffset: Int, initialHasMore: Boolean) {
        offset = initialOffset
        hasMore = initialHasMore
        loadingMore = false
    }

    suspend fun search(query: String) {
        if (query.isBlank()) return
        delay(250)
        val matchingGroupIds = withContext(Dispatchers.IO) { repository.searchFavoriteGroupIds(query) }
        val knownGroupIds = groups.mapTo(HashSet()) { it.id }
        if (matchingGroupIds.isNotEmpty()) {
            val missingGroups = withContext(Dispatchers.IO) {
                repository.loadFavoriteGroupsByIds(matchingGroupIds.filterNot { it in knownGroupIds })
            }
            groups.addAll(missingGroups)
        }
        val matches = withContext(Dispatchers.IO) { repository.searchFavoriteItems(query) }
        val knownIds = items.mapTo(HashSet()) { it.id }
        items.addAll(matches.filterNot { it.id in knownIds })
    }

    suspend fun loadMore(): Boolean {
        if (!hasMore || loadingMore) return false
        loadingMore = true
        return try {
            val page = withContext(Dispatchers.IO) {
                repository.loadFavoriteGroupPage(FAVORITE_GROUP_PAGE_SIZE, offset)
            }
            val knownIds = groups.mapTo(HashSet()) { it.id }
            groups.addAll(page.filterNot { it.id in knownIds })
            offset += page.size
            hasMore = page.size == FAVORITE_GROUP_PAGE_SIZE
            true
        } finally {
            loadingMore = false
        }
    }
}
