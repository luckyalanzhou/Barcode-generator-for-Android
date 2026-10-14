package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
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
) {
    internal val searchStore = LibraryStateStore()
    internal val coordinator = FavoritesQueryCoordinator(
        repository = repository,
        store = dataSession.store,
        searchStore = searchStore,
    )

    private val mutableSearchState = MutableStateFlow(BarcodeDataState())
    val searchState: StateFlow<BarcodeDataState> = mutableSearchState.asStateFlow()

    fun publishSearchState() {
        val currentData = dataSession.dataState.value
        mutableSearchState.value = coordinator.searchSnapshot(currentData.isReady).copy(
            folders = currentData.folders,
        )
    }

    internal fun onLibrarySnapshotLoaded(metadata: LibraryLoadMetadata) {
        coordinator.resetPaging(metadata.lastFavoriteGroupCursor, metadata.hasMoreFavoriteGroups)
        publishSearchState()
    }
}
