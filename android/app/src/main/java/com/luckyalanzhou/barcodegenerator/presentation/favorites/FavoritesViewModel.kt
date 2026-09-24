package com.luckyalanzhou.barcodegenerator.presentation.favorites

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.FavoriteTreeUiState

/** Owns transient Favorites-page state and observes the shared barcode data session. */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val dataSession: FavoritesDataSession,
) : ViewModel() {
    private val pageState = FavoritesPageStateCoordinator()

    val dataState: StateFlow<BarcodeDataState> = dataSession.dataState
    val searchState: StateFlow<BarcodeDataState> = dataSession.searchState
    val treeState: StateFlow<FavoriteTreeUiState> = pageState.treeState
    val query: StateFlow<String> = pageState.query

    fun updateQuery(query: String) = pageState.updateQuery(query)
    fun syncTree(folders: Set<String>) = pageState.syncTree(folders)
    fun addCollapsed(paths: Set<String>) = pageState.addCollapsed(paths)
    fun toggleFolder(path: String, folders: Set<String>) = pageState.toggleFolder(path, folders)
    fun updateSearch(expandedPaths: Set<String>, searching: Boolean) =
        pageState.updateSearch(expandedPaths, searching)
    fun position(): Pair<Int, Int> = pageState.position()
    fun rememberPosition(index: Int, offset: Int) = pageState.rememberPosition(index, offset)
}
