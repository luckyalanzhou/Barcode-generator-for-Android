package com.luckyalanzhou.barcodegenerator.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.FavoriteTreeUiState
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import java.util.Locale
import java.io.OutputStream

/**
 * 收藏页业务协调器：维护搜索、树展开与滚动位置，并把收藏文件/文件夹变更持久化到共享数据会话。
 * 页面只发起操作；此处统一负责校验、写入、刷新分页/搜索快照，并向界面反馈结果。
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val dataSession: LibraryDataSession,
    private val querySession: FavoritesQuerySession,
    private val barcodeDataCoordinator: BarcodeDataCoordinator,
    private val libraryDataCoordinator: LibraryDataCoordinator,
    private val persistence: BarcodePersistenceCoordinator,
    private val appLogger: AppLogger,
) : ViewModel() {
    private val pageState = FavoritesPageStateCoordinator()
    private val mutations = FavoritesMutationCoordinator(dataSession.store, persistence, querySession.searchStore) {
        dataSession.publishDataState()
        querySession.publishSearchState()
    }
    private val groupContent = FavoriteGroupContentCoordinator(
        loadContent = barcodeDataCoordinator.repository::loadFavoriteGroupContent,
        regularStore = dataSession.store,
        searchStore = querySession.searchStore,
        publishDataState = dataSession::publishDataState,
        publishSearchState = querySession::publishSearchState,
    )
    private var searchJob: Job? = null
    private var groupLoadJob: Job? = null
    private var groupLoadRequest = 0L
    private val scrollToTopRequests = Channel<Unit>(Channel.BUFFERED)

    val dataState: StateFlow<BarcodeDataState> = dataSession.dataState
    val searchState: StateFlow<BarcodeDataState> = querySession.searchState
    private val mutableSearchStatus = MutableStateFlow(FavoriteSearchStatus())
    val searchStatus: StateFlow<FavoriteSearchStatus> = mutableSearchStatus.asStateFlow()
    val treeState: StateFlow<FavoriteTreeUiState> = pageState.treeState
    val query: StateFlow<String> = pageState.query
    val scrollToTopEvents: Flow<Unit> = scrollToTopRequests.receiveAsFlow()
    init {
        viewModelScope.launch {
            dataSession.loadMetadata.collect { metadata ->
                if (metadata != null) querySession.onLibrarySnapshotLoaded(metadata)
            }
        }
    }

    fun searchFavoriteContent(query: String) {
        searchJob?.cancel()
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        mutableSearchStatus.value = FavoriteSearchStatus(normalizedQuery, busy = normalizedQuery.isNotEmpty())
        searchJob = viewModelScope.launch {
            try {
                querySession.coordinator.search(normalizedQuery)
                querySession.publishSearchState()
                mutableSearchStatus.value = FavoriteSearchStatus(normalizedQuery)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appLogger.record("favorites", "search failed", error)
                mutableSearchStatus.value = FavoriteSearchStatus(normalizedQuery, failed = true)
            }
        }
    }

    fun loadMoreFavoriteGroups(search: String) {
        viewModelScope.launch {
            if (search.isNotBlank()) {
                if (querySession.coordinator.loadMore(search)) {
                    querySession.publishSearchState()
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
    fun collapseAllFolders() {
        val current = dataState.value
        val folders = (current.folders + current.groups.map { it.folder })
            .filter { it.isNotBlank() }
            .toSet()
        pageState.collapseAllFolders(folders)
    }
    fun requestScrollToTop() {
        scrollToTopRequests.trySend(Unit)
    }
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
        persistence.persistFavoriteFolders(dataSession.store.foldersSnapshot())
        publishAfterMutation()
        return true
    }

    fun renameFavoriteFolder(path: String, renamedPath: String) {
        if (mutations.renameFolderAndPersist(path, renamedPath)) publishAfterMutation()
    }

    fun deleteFavoriteFolder(path: String) {
        mutations.deleteFolderAndPersist(path)
        publishAfterMutation()
    }

    fun renameFavoriteGroup(groupId: Long, name: String): Boolean {
        if (!mutations.renameGroupAndPersist(groupId, name)) return false
        publishAfterMutation()
        return true
    }

    fun moveFavoriteGroup(groupId: Long, folder: String): Boolean {
        if (!mutations.moveGroupAndPersist(groupId, folder)) return false
        publishAfterMutation()
        return true
    }

    fun deleteFavoriteGroup(groupId: Long) {
        mutations.deleteGroupAndPersist(groupId)
        publishAfterMutation()
    }

    /** 清空全部收藏并写入存储，随后刷新收藏树与搜索结果。 */
    fun clearFavorites() {
        mutations.clearFavoritesAndPersist()
        publishAfterMutation()
    }

    suspend fun inspectFavoriteImport(backup: InterchangeBackup): FavoritesImportConflictSummary =
        barcodeDataCoordinator.inspectFavoriteImport(backup)

    /** 按冲突策略导入收藏备份，随后通过分页快照重载数据，避免界面保留旧数据。 */
    suspend fun importFavorites(
        backup: InterchangeBackup,
        overwriteConflicts: Boolean = false,
    ): Pair<Int, Int> {
        val counts = barcodeDataCoordinator.importFavorites(backup, overwriteConflicts)
        // 导入后复用启动时的分页快照加载流程，避免界面继续显示旧分页数据。
        libraryDataCoordinator.loadPersistedData()
        return counts
    }

    /** 将收藏快照写入调用方提供的输出流；文件创建与目标位置由平台层负责。 */
    suspend fun exportFavorites(output: OutputStream) = barcodeDataCoordinator.exportFavorites(output)

    fun restoreFavorites(bytes: ByteArray): InterchangeBackup = barcodeDataCoordinator.restoreFavorites(bytes)

    /** 将结果页条码保存为指定文件或更新现有收藏文件；成功后同步刷新收藏视图。 */
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

    internal fun loadFavoriteGroupContent(
        group: FavoriteGroup,
        onLoaded: (FavoriteGroupContentLoadResult.Loaded) -> Unit,
        onNotice: (String) -> Unit,
    ) {
        groupLoadJob?.cancel()
        val request = ++groupLoadRequest
        groupLoadJob = viewModelScope.launch {
            try {
                appLogger.record("favorites", "open start groupId=${group.id}", null)
                val result = withContext(Dispatchers.IO) { groupContent.load(group.id) }
                if (request != groupLoadRequest) return@launch
                when (result) {
                    is FavoriteGroupContentLoadResult.Loaded -> {
                        groupContent.cache(result)
                        onLoaded(result)
                    }
                    is FavoriteGroupContentLoadResult.Rejected -> when (result.failure) {
                        FavoriteGroupContentLoadFailure.GROUP_NOT_CACHED -> {
                            appLogger.record("favorites", "open skipped groupId=${group.id} reason=group_not_loaded", null)
                            onNotice("收藏文件已变化，请刷新收藏列表后重试")
                        }
                        FavoriteGroupContentLoadFailure.GROUP_NOT_FOUND -> {
                            appLogger.record("favorites", "open failed groupId=${group.id} reason=group_missing_in_room", null)
                            onNotice("收藏文件已不存在，请刷新收藏列表")
                        }
                        FavoriteGroupContentLoadFailure.GROUP_CHANGED ->
                            appLogger.record("favorites", "open discarded groupId=${group.id} reason=group_changed_during_read", null)
                        FavoriteGroupContentLoadFailure.INVALID_CONTENT -> {
                            appLogger.record(
                                "favorites",
                                "open integrity failure groupId=${group.id} linked=${result.linkedCount} loaded=${result.loadedCount} invalid=${result.invalidCount}",
                                null,
                            )
                            onNotice("收藏文件数据不完整，已阻止打开空结果；请先导出备份并联系支持")
                        }
                        FavoriteGroupContentLoadFailure.EMPTY -> {
                            appLogger.record("favorites", "open empty groupId=${group.id} linked=0", null)
                            onNotice("该收藏文件没有条码内容，未进入结果页")
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appLogger.record("favorites", "open failed groupId=${group.id}", error)
                onNotice("读取收藏文件失败，数据未被修改；请重试")
            }
        }
    }

    internal fun cancelPendingGroupLoad() {
        groupLoadRequest++
        groupLoadJob?.cancel()
        groupLoadJob = null
    }

    internal fun isFavoriteGroupCurrent(groupId: Long, savedAt: Long): Boolean =
        groupContent.isCurrent(groupId, savedAt)

    private fun publishAfterMutation() {
        querySession.coordinator.onMutation()
        querySession.publishSearchState()
        dataSession.publishDataState()
        if (query.value.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                persistence.awaitPendingWrites()
                querySession.coordinator.clearSearch()
                querySession.coordinator.search(query.value.trim().lowercase(Locale.ROOT))
                querySession.publishSearchState()
            }
        }
    }
}
