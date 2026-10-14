package com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeState

/** Feature-owned snapshot of favorite data used to render the page. */
internal data class FavoritesListContentState(
    val items: List<CodeItem> = emptyList(),
    val groups: List<FavoriteGroup> = emptyList(),
    val folders: List<String> = emptyList(),
    val isReady: Boolean = false,
)

/** Search feedback needed by the favorites UI, without exposing its ViewModel state type. */
internal data class FavoritesSearchContentState(
    val query: String = "",
    val busy: Boolean = false,
    val failed: Boolean = false,
) {
    fun isPending(currentQuery: String): Boolean =
        currentQuery.isNotEmpty() && (query != currentQuery || busy)
}

/** Complete render input for the favorites page; events and mutations stay callback-driven. */
internal data class FavoritesContentState(
    val favorites: FavoritesListContentState = FavoritesListContentState(),
    val searchResults: FavoritesListContentState = FavoritesListContentState(),
    val search: FavoritesSearchContentState = FavoritesSearchContentState(),
    val tree: FavoritesTreeState = FavoritesTreeState(),
    val query: String = "",
    val savedListPosition: Pair<Int, Int> = 0 to 0,
)

internal fun favoriteEmptyMessage(query: String): String =
    if (query.isNotEmpty()) "没有匹配的收藏" else "还没有收藏"
