package com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeState

/** 由收藏功能持有、用于渲染页面的收藏数据快照。 */
internal data class FavoritesListContentState(
    val items: List<CodeItem> = emptyList(),
    val groups: List<FavoriteGroup> = emptyList(),
    val folders: List<String> = emptyList(),
    val isReady: Boolean = false,
    val loadFailed: Boolean = false,
)

/** 收藏界面所需的搜索反馈，不暴露 ViewModel 的状态类型。 */
internal data class FavoritesSearchContentState(
    val query: String = "",
    val busy: Boolean = false,
    val failed: Boolean = false,
    val paginationFailed: Boolean = false,
) {
    fun isPending(currentQuery: String): Boolean =
        currentQuery.isNotEmpty() && (query != currentQuery || busy)
}

/** 收藏页的完整渲染输入；事件与数据变更仍通过回调处理。 */
internal data class FavoritesContentState(
    val favorites: FavoritesListContentState = FavoritesListContentState(),
    val searchResults: FavoritesListContentState = FavoritesListContentState(),
    val search: FavoritesSearchContentState = FavoritesSearchContentState(),
    val tree: FavoritesTreeState = FavoritesTreeState(),
    val query: String = "",
    val savedListPosition: Pair<Int, Int> = 0 to 0,
    val searchRefreshGeneration: Long = 0L,
)

internal fun favoriteEmptyMessage(query: String): String =
    if (query.isNotEmpty()) "没有匹配的收藏" else "还没有收藏"
