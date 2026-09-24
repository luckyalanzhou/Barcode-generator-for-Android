package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.presentation.*

import kotlinx.coroutines.flow.StateFlow

/** Owns Favorites page-only UI state; it does not read or mutate favorite data. */
internal class FavoritesPageStateCoordinator {
    private val treeCoordinator = FavoriteTreeCoordinator()
    private val searchQueryState = FavoriteSearchQueryState()
    val treeState: StateFlow<FavoriteTreeUiState> = treeCoordinator.state
    val query: StateFlow<String> = searchQueryState.query

    private var listPositionIndex = 0
    private var listPositionOffset = 0

    fun syncTree(folders: Set<String>) = treeCoordinator.sync(folders)
    fun updateQuery(query: String) = searchQueryState.update(query)
    fun addCollapsed(paths: Set<String>) = treeCoordinator.addCollapsed(paths)
    fun toggleFolder(path: String, folders: Set<String>) = treeCoordinator.toggle(path, folders)
    fun updateSearch(expandedPaths: Set<String>, searching: Boolean) =
        treeCoordinator.updateSearch(expandedPaths, searching)

    fun position(): Pair<Int, Int> = listPositionIndex to listPositionOffset

    fun rememberPosition(index: Int, offset: Int) {
        listPositionIndex = index.coerceAtLeast(0)
        listPositionOffset = offset.coerceAtLeast(0)
    }
}
