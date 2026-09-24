package com.luckyalanzhou.barcodegenerator.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.FavoriteTreeUiState
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import java.util.Locale

/** Owns transient Favorites-page state and observes the shared barcode data session. */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val dataSession: FavoritesDataSession,
    private val querySession: FavoritesQuerySession,
    private val barcodeDataCoordinator: BarcodeDataCoordinator,
    private val persistence: BarcodePersistenceCoordinator,
    private val appLogger: AppLogger,
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
    private val groupContent = FavoriteGroupContentCoordinator(
        loadContent = barcodeDataCoordinator.repository::loadFavoriteGroupContent,
        regularStore = dataSession.store,
        searchStore = dataSession.searchStore,
        publishDataState = dataSession::publishDataState,
        publishSearchState = { dataSession.publishSearchState(querySession.coordinator) },
    )
    private var searchJob: Job? = null
    private var groupLoadJob: Job? = null
    private var groupLoadRequest = 0L

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

    internal fun loadFavoriteGroupContent(
        group: FavoriteGroup,
        onLoaded: (FavoriteGroupContentLoadResult.Loaded) -> Unit,
        onNotice: (String) -> Unit,
    ) {
        groupLoadJob?.cancel()
        val request = ++groupLoadRequest
        groupLoadJob = viewModelScope.launch {
            try {
                DebugLog.record("favorites", "open start groupId=${group.id}")
                val result = withContext(Dispatchers.IO) { groupContent.load(group.id) }
                if (request != groupLoadRequest) return@launch
                when (result) {
                    is FavoriteGroupContentLoadResult.Loaded -> {
                        groupContent.cache(result)
                        onLoaded(result)
                    }
                    is FavoriteGroupContentLoadResult.Rejected -> when (result.failure) {
                        FavoriteGroupContentLoadFailure.GROUP_NOT_CACHED -> {
                            DebugLog.record("favorites", "open skipped groupId=${group.id} reason=group_not_loaded")
                            onNotice("收藏文件已变化，请刷新收藏列表后重试")
                        }
                        FavoriteGroupContentLoadFailure.GROUP_NOT_FOUND -> {
                            DebugLog.record("favorites", "open failed groupId=${group.id} reason=group_missing_in_room")
                            onNotice("收藏文件已不存在，请刷新收藏列表")
                        }
                        FavoriteGroupContentLoadFailure.GROUP_CHANGED ->
                            DebugLog.record("favorites", "open discarded groupId=${group.id} reason=group_changed_during_read")
                        FavoriteGroupContentLoadFailure.INVALID_CONTENT -> {
                            DebugLog.record(
                                "favorites",
                                "open integrity failure groupId=${group.id} linked=${result.linkedCount} loaded=${result.loadedCount} invalid=${result.invalidCount}",
                            )
                            onNotice("收藏文件数据不完整，已阻止打开空结果；请先导出备份并联系支持")
                        }
                        FavoriteGroupContentLoadFailure.EMPTY -> {
                            DebugLog.record("favorites", "open empty groupId=${group.id} linked=0")
                            onNotice("该收藏文件没有条码内容，未进入结果页")
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appLogger.record("favorites", "open failed groupId=${group.id}", error)
                DebugLog.record("favorites", "open failed groupId=${group.id}", error)
                onNotice("读取收藏文件失败，数据未被修改；请重试")
            }
        }
    }

    internal fun isFavoriteGroupCurrent(groupId: Long, savedAt: Long): Boolean =
        groupContent.isCurrent(groupId, savedAt)

    private fun publishAfterMutation() {
        querySession.coordinator.onMutation()
        dataSession.publishDataState()
    }
}
