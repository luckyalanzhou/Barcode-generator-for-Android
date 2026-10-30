package com.luckyalanzhou.barcodegenerator.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
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
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryLoadMetadata
import com.luckyalanzhou.barcodegenerator.presentation.shared.recordSafely
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
    private val mutations = FavoritesMutationCoordinator(
        store = dataSession.store,
        persistence = persistence,
        onReconciled = querySession::onLibraryMutation,
        onMutationStarted = querySession::invalidateGroupContentLoads,
    )
    private var searchJob: Job? = null
    private var groupLoadJob: Job? = null
    private var groupLoadRequest = 0L
    private val scrollToTopRequests = Channel<Unit>(Channel.BUFFERED)

    val dataState: StateFlow<BarcodeDataState> = dataSession.dataState
    internal val snapshotMetadata: StateFlow<LibraryLoadMetadata?> = dataSession.loadMetadata

    internal fun favoriteGroupIdForIdentity(
        folder: String,
        name: String,
        excludingIds: Set<Long> = emptySet(),
    ): Long? = dataSession.store.favoriteGroupIdForIdentity(folder, name, excludingIds)

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
                querySession.search(normalizedQuery)
                mutableSearchStatus.value = FavoriteSearchStatus(normalizedQuery)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appLogger.recordSafely("favorites", "search failed", error, error)
                mutableSearchStatus.value = FavoriteSearchStatus(normalizedQuery, failed = true)
            }
        }
    }

    fun loadMoreFavoriteGroups(search: String) {
        viewModelScope.launch {
            try {
                querySession.loadMore(search)
                mutableSearchStatus.value = mutableSearchStatus.value.let { status ->
                    if (status.query == search) status.copy(paginationFailed = false) else status
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                appLogger.recordSafely("favorites", "pagination_load_failed search=${search.isNotBlank()}", error, error)
                mutableSearchStatus.value = mutableSearchStatus.value.let { status ->
                    if (status.query == search) status.copy(paginationFailed = true) else status
                }
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

    suspend fun createFavoriteFolder(path: String): Boolean = withDurableMutation {
        val store = dataSession.store
        if (!store.canAddFolderIfAbsent(path)) return@withDurableMutation false
        val foldersToPersist = store.foldersSnapshot().toMutableList().apply {
            if (path !in this) add(path)
        }
        try {
            persistence.persistFavoriteFolders(foldersToPersist).await().getOrThrow()
        } catch (cancelled: CancellationException) {
            restoreAfterMutationFailure(cancelled)
            throw cancelled
        } catch (error: Exception) {
            restoreAfterMutationFailure(error)
            throw error
        }
        store.addPersistedFolderIfAbsent(path)
        publishAfterMutation()
        true
    }

    suspend fun renameFavoriteFolder(path: String, renamedPath: String): Boolean = withDurableMutation {
        try {
            if (!mutations.renameFolderAndPersist(path, renamedPath)) return@withDurableMutation false
        } catch (cancelled: CancellationException) {
            restoreAfterMutationFailure(cancelled)
            throw cancelled
        } catch (error: Exception) {
            restoreAfterMutationFailure(error)
            throw error
        }
        publishAfterMutation()
        true
    }

    suspend fun deleteFavoriteFolder(path: String) = withDurableMutation {
        try {
            mutations.deleteFolderAndPersist(path)
        } catch (cancelled: CancellationException) {
            restoreAfterMutationFailure(cancelled)
            throw cancelled
        } catch (error: Exception) {
            restoreAfterMutationFailure(error)
            throw error
        }
        publishAfterMutation()
    }

    suspend fun renameFavoriteGroup(groupId: Long, name: String): Boolean = withDurableMutation {
        try {
            if (!mutations.renameGroupAndPersist(groupId, name)) return@withDurableMutation false
        } catch (cancelled: CancellationException) {
            restoreAfterMutationFailure(cancelled)
            throw cancelled
        } catch (error: Exception) {
            restoreAfterMutationFailure(error)
            throw error
        }
        publishAfterMutation()
        true
    }

    suspend fun moveFavoriteGroup(groupId: Long, folder: String): Boolean = withDurableMutation {
        try {
            if (!mutations.moveGroupAndPersist(groupId, folder)) return@withDurableMutation false
        } catch (cancelled: CancellationException) {
            restoreAfterMutationFailure(cancelled)
            throw cancelled
        } catch (error: Exception) {
            restoreAfterMutationFailure(error)
            throw error
        }
        publishAfterMutation()
        true
    }

    suspend fun deleteFavoriteGroup(groupId: Long) = withDurableMutation {
        try {
            mutations.deleteGroupAndPersist(groupId)
        } catch (cancelled: CancellationException) {
            restoreAfterMutationFailure(cancelled)
            throw cancelled
        } catch (error: Exception) {
            restoreAfterMutationFailure(error)
            throw error
        }
        publishAfterMutation()
    }

    /** 清空全部收藏并写入存储，随后刷新收藏树与搜索结果。 */
    suspend fun clearFavorites() = withDurableMutation {
        try {
            mutations.clearFavoritesAndPersist()
        } catch (cancelled: CancellationException) {
            restoreAfterMutationFailure(cancelled)
            throw cancelled
        } catch (error: Exception) {
            restoreAfterMutationFailure(error)
            throw error
        }
        publishAfterMutation()
    }

    suspend fun inspectFavoriteImport(backup: InterchangeBackup): FavoritesImportConflictSummary =
        barcodeDataCoordinator.inspectFavoriteImport(backup)

    /** 按冲突策略导入收藏备份，随后通过分页快照重载数据，避免界面保留旧数据。 */
    suspend fun importFavorites(
        backup: InterchangeBackup,
        overwriteConflicts: Boolean = false,
    ): Pair<Int, Int> = withDurableMutation {
        // 冲突规划和导入提交前先排空共享写队列，防止较早捕获的收藏快照在导入后回写。
        persistence.awaitPendingWrites()
        querySession.invalidateGroupContentLoads()
        val counts = barcodeDataCoordinator.importFavorites(backup, overwriteConflicts)
        // 导入事务已提交；共享数据协调器只重试快照读取，不会再次执行导入事务。
        try {
            libraryDataCoordinator.loadPersistedData()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw FavoritesImportRefreshException(counts.first, counts.second, error)
        }
        // 在共享写锁释放前应用新游标，避免旧分页请求在快照刷新与 StateFlow 收集器之间启动。
        querySession.onLibrarySnapshotLoaded(requireNotNull(dataSession.loadMetadata.value))
        querySession.invalidateGroupContentLoads()
        counts
    }

    /** 将收藏快照写入调用方提供的输出流；文件创建与目标位置由平台层负责。 */
    suspend fun exportFavorites(output: OutputStream) = barcodeDataCoordinator.exportFavorites(output)

    fun restoreFavorites(bytes: ByteArray): InterchangeBackup = barcodeDataCoordinator.restoreFavorites(bytes)

    /** 将结果页条码保存为指定文件或更新现有收藏文件；成功后同步刷新收藏视图。 */
    suspend fun saveResultAsFavorite(
        resultItemIds: List<Long>,
        editingGroupId: Long?,
        targetGroupId: Long?,
        folder: String,
        name: String,
    ): Boolean = withDurableMutation {
        try {
            if (!mutations.saveResultAsFavorite(resultItemIds, editingGroupId, targetGroupId, folder, name)) return@withDurableMutation false
        } catch (cancelled: CancellationException) {
            restoreAfterMutationFailure(cancelled)
            throw cancelled
        } catch (error: Exception) {
            // 等恢复完成再允许弹窗重试，避免失败的乐观快照被误判为重名。
            restoreAfterMutationFailure(error)
            throw error
        }
        publishAfterMutation()
        true
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
                appLogger.recordSafely("favorites", "open start groupId=${group.id}")
                val result = withContext(Dispatchers.IO) { querySession.loadGroupContent(group.id) }
                if (request != groupLoadRequest) return@launch
                when (result) {
                    is FavoriteGroupContentLoadResult.Loaded -> {
                        if (!querySession.cacheGroupContent(result)) {
                            appLogger.recordSafely("favorites", "open discarded groupId=${group.id} reason=stale_before_cache")
                            return@launch
                        }
                        onLoaded(result)
                    }
                    is FavoriteGroupContentLoadResult.Rejected -> when (result.failure) {
                        FavoriteGroupContentLoadFailure.GROUP_NOT_CACHED -> {
                            appLogger.recordSafely("favorites", "open skipped groupId=${group.id} reason=group_not_loaded")
                            onNotice("收藏文件已变化，请刷新收藏列表后重试")
                        }
                        FavoriteGroupContentLoadFailure.GROUP_NOT_FOUND -> {
                            appLogger.recordSafely("favorites", "open failed groupId=${group.id} reason=group_missing_in_room")
                            onNotice("收藏文件已不存在，请刷新收藏列表")
                        }
                        FavoriteGroupContentLoadFailure.GROUP_CHANGED ->
                            appLogger.recordSafely("favorites", "open discarded groupId=${group.id} reason=group_changed_during_read")
                        FavoriteGroupContentLoadFailure.INVALID_CONTENT -> {
                            appLogger.recordSafely(
                                "favorites",
                                "open integrity failure groupId=${group.id} linked=${result.linkedCount} loaded=${result.loadedCount} invalid=${result.invalidCount}",
                            )
                            onNotice("收藏文件数据不完整，已阻止打开空结果；请先导出备份并联系支持")
                        }
                        FavoriteGroupContentLoadFailure.EMPTY -> {
                            appLogger.recordSafely("favorites", "open empty groupId=${group.id} linked=0")
                            onNotice("该收藏文件没有条码内容，未进入结果页")
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appLogger.recordSafely("favorites", "open failed groupId=${group.id}", error, error)
                onNotice("读取收藏文件失败，数据未被修改；请重试")
            }
        }
    }

    internal fun cancelPendingGroupLoad() {
        groupLoadRequest++
        groupLoadJob?.cancel()
        groupLoadJob = null
    }

    internal fun isFavoriteGroupCurrent(groupId: Long, savedAt: Long, mutationGeneration: Long): Boolean =
        querySession.isGroupCurrent(groupId, savedAt, mutationGeneration)

    private fun publishAfterMutation() {
        querySession.onLibraryMutation()
        if (query.value.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                try {
                    querySession.refreshSearchAfterMutation()
                    mutableSearchStatus.value = FavoriteSearchStatus(query.value.trim().lowercase(Locale.ROOT))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    appLogger.recordSafely("favorites", "search refresh failed after mutation", error, error)
                    mutableSearchStatus.value = FavoriteSearchStatus(
                        query.value.trim().lowercase(Locale.ROOT),
                        failed = true,
                    )
                }
            }
        }
    }

    private suspend fun restoreAfterMutationFailure(error: Exception) {
        try {
            libraryDataCoordinator.loadPersistedData()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (reloadError: Exception) {
            if (reloadError !== error) error.addSuppressed(reloadError)
        }
    }

    /** 等待进程级写入队列回执并发布新状态，避免 Activity 重建取消等待者后漏发状态。 */
    private suspend fun <T> withDurableMutation(block: suspend () -> T): T =
        withContext(NonCancellable) { persistence.withLibraryMutation(block) }
}
