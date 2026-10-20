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

/** Keeps one query cursor set aligned with the shared regular and search stores. */
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
        val loaded = coordinator.loadMore(query)
        if (loaded) {
            if (query.isNotBlank()) publishSearchState()
            dataSession.publishDataState()
        }
        return loaded
    }

    /** Updates the shared page cursor and publishes snapshots after an in-memory library mutation. */
    internal fun onLibraryMutation() {
        coordinator.onMutation()
        dataSession.publishDataState()
        publishSearchState()
    }

    /** Waits for queued writes before refreshing the active search projection from persisted data. */
    internal suspend fun refreshSearchAfterMutation() {
        persistence.awaitPendingWrites()
        if (coordinator.refreshActiveSearch()) publishSearchState()
    }

    internal suspend fun loadGroupContent(groupId: Long): FavoriteGroupContentLoadResult = groupContent.load(groupId)

    internal fun cacheGroupContent(loaded: FavoriteGroupContentLoadResult.Loaded) = groupContent.cache(loaded)

    internal fun isGroupCurrent(groupId: Long, savedAt: Long): Boolean = groupContent.isCurrent(groupId, savedAt)

    internal suspend fun onLibrarySnapshotLoaded(metadata: LibraryLoadMetadata) {
        coordinator.resetPaging(metadata.lastFavoriteGroupCursor, metadata.hasMoreFavoriteGroups)
        publishSearchState()
    }
}
