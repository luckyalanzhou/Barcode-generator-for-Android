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
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import java.util.Locale

/** Owns transient Favorites-page state and observes the shared barcode data session. */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val dataSession: FavoritesDataSession,
    private val querySession: FavoritesQuerySession,
    private val barcodeDataCoordinator: BarcodeDataCoordinator,
    private val persistence: BarcodePersistenceCoordinator,
) : ViewModel() {
    private val pageState = FavoritesPageStateCoordinator()
    private val mutations = FavoritesMutationCoordinator(dataSession.store, persistence, viewModelScope)
    private val loader = FavoritesLoadCoordinator(
        persistence = persistence,
        store = dataSession.store,
        query = querySession.coordinator,
        publish = dataSession::publishDataState,
        publishSearch = { dataSession.publishSearchState(querySession.coordinator) },
    )
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

    fun createFavoriteFolder(path: String): Boolean {
        val added = dataSession.store.edit {
            if (path.isBlank() || path in folders) return@edit false
            folders.add(path)
            true
        }
        if (!added) return false
        persistence.persistFavoriteFolders(viewModelScope, dataSession.store.foldersSnapshot())
        publishAfterMutation()
        return true
    }

    fun renameFavoriteFolder(path: String, renamedPath: String) {
        mutations.renameFolderAndPersist(path, renamedPath)
        publishAfterMutation()
    }

    fun deleteFavoriteFolder(path: String) {
        mutations.deleteFolderAndPersist(path)
        publishAfterMutation()
    }

    fun renameFavoriteGroup(groupId: Long, name: String) {
        mutations.renameGroupAndPersist(groupId, name)
        publishAfterMutation()
    }

    fun moveFavoriteGroup(groupId: Long, folder: String) {
        mutations.moveGroupAndPersist(groupId, folder)
        publishAfterMutation()
    }

    fun deleteFavoriteGroup(groupId: Long) {
        mutations.deleteGroupAndPersist(groupId)
        publishAfterMutation()
    }

    fun clearFavorites() {
        mutations.clearFavoritesAndPersist()
        publishAfterMutation()
    }

    suspend fun inspectFavoriteImport(backup: InterchangeBackup): FavoritesImportConflictSummary =
        barcodeDataCoordinator.inspectFavoriteImport(backup)

    suspend fun importFavorites(
        backup: InterchangeBackup,
        overwriteConflicts: Boolean = false,
    ): Pair<Int, Int> {
        val counts = barcodeDataCoordinator.importFavorites(backup, overwriteConflicts)
        // Reload through the same paging-aware snapshot path used at startup.
        loader.loadPersistedData()
        return counts
    }

    suspend fun exportFavorites(): ByteArray = barcodeDataCoordinator.exportFavorites()

    fun restoreFavorites(bytes: ByteArray): InterchangeBackup = barcodeDataCoordinator.restoreFavorites(bytes)

    fun saveResultAsFavorite(
        resultItemIds: List<Long>,
        editingGroupId: Long?,
        targetGroupId: Long?,
        folder: String,
        name: String,
    ): Boolean {
        if (!mutations.saveResultAsFavorite(resultItemIds, editingGroupId, targetGroupId, folder, name)) return false
        publishAfterMutation()
        return true
    }

    fun updateFavoriteGroup(groupId: Long, name: String, folder: String): Boolean {
        if (dataSession.store.groupsSnapshot().none { it.id == groupId }) return false
        if (!mutations.updateGroup(groupId, name, folder)) return false
        publishAfterMutation()
        return true
    }

    private fun publishAfterMutation() {
        querySession.coordinator.onMutation()
        dataSession.publishDataState()
    }
}
