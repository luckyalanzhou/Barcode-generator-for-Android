package com.luckyalanzhou.barcodegenerator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns Favorites page-only UI state; it does not read or mutate favorite data. */
internal class FavoritesPageStateCoordinator {
    private val treeCoordinator = FavoriteTreeCoordinator()
    val treeState: StateFlow<FavoriteTreeUiState> = treeCoordinator.state

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var listPositionIndex = 0
    private var listPositionOffset = 0

    fun syncTree(folders: Set<String>) = treeCoordinator.sync(folders)
    fun addCollapsed(paths: Set<String>) = treeCoordinator.addCollapsed(paths)
    fun toggleFolder(path: String, folders: Set<String>) = treeCoordinator.toggle(path, folders)
    fun updateSearch(expandedPaths: Set<String>, searching: Boolean) =
        treeCoordinator.updateSearch(expandedPaths, searching)

    fun updateQuery(value: String) {
        _query.value = value
    }

    fun position(): Pair<Int, Int> = listPositionIndex to listPositionOffset

    fun rememberPosition(index: Int, offset: Int) {
        listPositionIndex = index.coerceAtLeast(0)
        listPositionOffset = offset.coerceAtLeast(0)
    }
}
