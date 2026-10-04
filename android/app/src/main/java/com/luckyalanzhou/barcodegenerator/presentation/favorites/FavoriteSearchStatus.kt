package com.luckyalanzhou.barcodegenerator.presentation.favorites

/** Query identity prevents results for the previous text being presented as a new empty search. */
data class FavoriteSearchStatus(val query: String = "", val busy: Boolean = false, val failed: Boolean = false) {
    fun isPending(currentQuery: String): Boolean =
        currentQuery.isNotEmpty() && (query != currentQuery || busy)
}

internal fun favoriteEmptyMessage(query: String): String =
    if (query.isNotEmpty()) "没有匹配的收藏" else "还没有收藏"
