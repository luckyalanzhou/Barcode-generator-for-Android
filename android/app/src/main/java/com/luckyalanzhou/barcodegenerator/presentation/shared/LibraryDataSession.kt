package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 持有各功能 ViewModel 共同观察的内存收藏库快照。 */
@ActivityRetainedScoped
class LibraryDataSession @Inject constructor() {
    internal val store = LibraryStateStore()

    private var loadGeneration = 0L
    private val mutableLoadMetadata = MutableStateFlow<LibraryLoadMetadata?>(null)
    val loadMetadata: StateFlow<LibraryLoadMetadata?> = mutableLoadMetadata.asStateFlow()

    private val mutableDataState = MutableStateFlow(BarcodeDataState())
    val dataState: StateFlow<BarcodeDataState> = mutableDataState.asStateFlow()

    fun publishDataState(
        isReady: Boolean = dataState.value.isReady,
        loadFailed: Boolean = dataState.value.loadFailed,
    ) {
        mutableDataState.value = store.snapshot(isReady).copy(loadFailed = loadFailed)
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
