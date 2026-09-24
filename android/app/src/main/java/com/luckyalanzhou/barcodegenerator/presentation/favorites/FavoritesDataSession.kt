package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeDataStateCoordinator
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the single in-memory barcode/favorites snapshot shared by feature ViewModels. */
@ActivityRetainedScoped
class FavoritesDataSession @Inject constructor() {
    internal val store = FavoritesStateStore()
    internal val searchStore = FavoritesStateStore()

    private val mutableDataState = MutableStateFlow(BarcodeDataState())
    val dataState: StateFlow<BarcodeDataState> = mutableDataState.asStateFlow()

    private val mutableSearchState = MutableStateFlow(BarcodeDataState())
    val searchState: StateFlow<BarcodeDataState> = mutableSearchState.asStateFlow()

    private val dataStateCoordinator = BarcodeDataStateCoordinator(store, mutableDataState)

    fun publishDataState(isReady: Boolean = dataState.value.isReady) {
        dataStateCoordinator.publish(isReady)
    }

    internal fun publishSearchState(query: FavoritesQueryCoordinator) {
        val currentData = dataState.value
        mutableSearchState.value = query.searchSnapshot(currentData.isReady).copy(
            folders = currentData.folders,
        )
    }
}
