package com.luckyalanzhou.barcodegenerator.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.FavoriteTreeUiState
import java.util.Locale

/** Owns transient Favorites-page state and observes the shared barcode data session. */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val dataSession: FavoritesDataSession,
    private val querySession: FavoritesQuerySession,
) : ViewModel() {
    private val pageState = FavoritesPageStateCoordinator()
    private var searchJob: Job? = null

    val dataState: StateFlow<BarcodeDataState> = dataSession.dataState
    val searchState: StateFlow<BarcodeDataState> = dataSession.searchState
    val treeState: StateFlow<FavoriteTreeUiState> = pageState.treeState
    val query: StateFlow<String> = pageState.query

    fun searchFavoriteContent(query: String) {
        searchJob?.cancel()
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        searchJob = viewModelScope.launch {
            querySession.coordinator.search(normalizedQuery)
            dataSession.publishSearchState(querySession.coordinator)
        }
    }

    fun loadMoreFavoriteGroups(search: String) {
        viewModelScope.launch {
            if (search.isNotBlank()) {
                if (querySession.coordinator.loadMore(search)) {
                    dataSession.publishSearchState(querySession.coordinator)
                    dataSession.publishDataState()
                }
            } else if (querySession.coordinator.loadMore()) {
                dataSession.publishDataState()
            }
        }
    }

    fun updateQuery(query: String) = pageState.updateQuery(query)
    fun syncTree(folders: Set<String>) = pageState.syncTree(folders)
    fun addCollapsed(paths: Set<String>) = pageState.addCollapsed(paths)
    fun toggleFolder(path: String, folders: Set<String>) = pageState.toggleFolder(path, folders)
    fun updateSearch(expandedPaths: Set<String>, searching: Boolean) =
        pageState.updateSearch(expandedPaths, searching)
    fun position(): Pair<Int, Int> = pageState.position()
    fun rememberPosition(index: Int, offset: Int) = pageState.rememberPosition(index, offset)
}
