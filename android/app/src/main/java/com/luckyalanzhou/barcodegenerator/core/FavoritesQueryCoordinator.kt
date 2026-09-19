package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FAVORITE_GROUP_PAGE_SIZE = 100

/** 收藏列表的查询边界：分页、搜索和延迟加载，不处理收藏变更。 */
class FavoritesQueryCoordinator(
    private val repository: BarcodeRepository,
    private val scope: CoroutineScope,
    private val items: MutableList<CodeItem>,
    private val groups: MutableList<FavoriteGroup>,
    private val publish: () -> Unit,
) {
    private var offset = 0
    private var hasMore = false
    private var loadingMore = false
    private var searchJob: Job? = null

    fun resetPaging(initialOffset: Int, initialHasMore: Boolean) {
        offset = initialOffset
        hasMore = initialHasMore
        loadingMore = false
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) return
        searchJob = scope.launch {
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
            publish()
        }
    }

    fun loadMore() {
        if (!hasMore || loadingMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    repository.loadFavoriteGroupPage(FAVORITE_GROUP_PAGE_SIZE, offset)
                }
                val knownIds = groups.mapTo(HashSet()) { it.id }
                groups.addAll(page.filterNot { it.id in knownIds })
                offset += page.size
                hasMore = page.size == FAVORITE_GROUP_PAGE_SIZE
                publish()
            } finally {
                loadingMore = false
            }
        }
    }
}
