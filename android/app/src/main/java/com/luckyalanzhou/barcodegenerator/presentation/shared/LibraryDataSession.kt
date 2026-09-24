package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the shared in-memory library snapshot observed by feature ViewModels. */
@ActivityRetainedScoped
class LibraryDataSession @Inject constructor() {
    internal val store = LibraryStateStore()

    private var loadGeneration = 0L
    private val mutableLoadMetadata = MutableStateFlow<LibraryLoadMetadata?>(null)
    val loadMetadata: StateFlow<LibraryLoadMetadata?> = mutableLoadMetadata.asStateFlow()

    private val mutableDataState = MutableStateFlow(BarcodeDataState())
    val dataState: StateFlow<BarcodeDataState> = mutableDataState.asStateFlow()

    private val dataStateCoordinator = BarcodeDataStateCoordinator(store, mutableDataState)

    fun publishDataState(isReady: Boolean = dataState.value.isReady) {
        dataStateCoordinator.publish(isReady)
    }

    internal fun publishLoadedSnapshot(cursor: FavoriteGroupPageCursor?, hasMoreGroups: Boolean) = synchronized(this) {
        mutableLoadMetadata.value = LibraryLoadMetadata(++loadGeneration, cursor, hasMoreGroups)
    }
}

data class LibraryLoadMetadata(
    val generation: Long,
    val lastFavoriteGroupCursor: FavoriteGroupPageCursor?,
    val hasMoreFavoriteGroups: Boolean,
)
