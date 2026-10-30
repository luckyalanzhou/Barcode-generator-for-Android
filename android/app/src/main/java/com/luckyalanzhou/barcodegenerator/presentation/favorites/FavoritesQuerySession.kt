package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryLoadMetadata
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 让同一组查询游标与共享的普通列表和搜索结果存储保持一致。 */
@ActivityRetainedScoped
class FavoritesQuerySession @Inject constructor(
    repository: BarcodeRepository,
    private val dataSession: LibraryDataSession,
    private val persistence: BarcodePersistenceCoordinator,
) {
    private val searchStore = LibraryStateStore()
    private val coordinator = FavoritesQueryCoordinator(
        repository = repository,
        store = dataSession.store,
        searchStore = searchStore,
    )
    private val groupContent = FavoriteGroupContentCoordinator(
        loadContent = repository::loadFavoriteGroupContent,
        regularStore = dataSession.store,
        searchStore = searchStore,
        publishDataState = dataSession::publishDataState,
        publishSearchState = ::publishSearchState,
    )

    private val mutableSearchState = MutableStateFlow(BarcodeDataState())
    internal val searchState: StateFlow<BarcodeDataState> = mutableSearchState.asStateFlow()
    private val snapshotResetMutex = Mutex()
    private var appliedSnapshotGeneration = Long.MIN_VALUE

    private fun publishSearchState() {
        val currentData = dataSession.dataState.value
        mutableSearchState.value = coordinator.searchSnapshot(currentData.isReady).copy(
            folders = currentData.folders,
        )
    }

    internal suspend fun search(query: String) {
        coordinator.search(query)
        publishSearchState()
    }

    internal suspend fun loadMore(query: String): Boolean {
        // 分页会向共享快照追加数据；查询和追加期间不能与导入或其他写入交错。
        val loaded = persistence.withLibraryMutation { coordinator.loadMore(query) }
        if (loaded) {
            if (query.isNotBlank()) publishSearchState()
            dataSession.publishDataState()
        }
        return loaded
    }

    /** 收藏库内存数据变更后，更新共享分页游标并发布快照。 */
    internal fun onLibraryMutation() {
        groupContent.onLibraryMutation()
        coordinator.onMutation()
        dataSession.publishDataState()
        publishSearchState()
    }

    /** 等待排队写入完成，再从持久化数据刷新当前搜索结果视图。 */
    internal suspend fun refreshSearchAfterMutation() {
        persistence.awaitPendingWrites()
        if (coordinator.refreshActiveSearch()) publishSearchState()
    }

    internal suspend fun loadGroupContent(groupId: Long): FavoriteGroupContentLoadResult = groupContent.load(groupId)

    internal fun cacheGroupContent(loaded: FavoriteGroupContentLoadResult.Loaded): Boolean = groupContent.cache(loaded)

    internal fun invalidateGroupContentLoads() = groupContent.onLibraryMutation()

    internal fun isGroupCurrent(groupId: Long, savedAt: Long, mutationGeneration: Long): Boolean =
        groupContent.isCurrent(groupId, savedAt, mutationGeneration)

    internal suspend fun onLibrarySnapshotLoaded(metadata: LibraryLoadMetadata) {
        snapshotResetMutex.withLock {
            if (metadata.generation <= appliedSnapshotGeneration) return@withLock
            coordinator.resetPaging(metadata.lastFavoriteGroupCursor, metadata.hasMoreFavoriteGroups)
            appliedSnapshotGeneration = metadata.generation
            publishSearchState()
        }
    }
}
