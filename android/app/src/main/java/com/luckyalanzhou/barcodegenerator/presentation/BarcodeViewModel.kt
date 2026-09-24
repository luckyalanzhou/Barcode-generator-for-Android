package com.luckyalanzhou.barcodegenerator.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.luckyalanzhou.barcodegenerator.presentation.favorites.*
import com.luckyalanzhou.barcodegenerator.presentation.shared.*
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesDataSession

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val barcodeDataCoordinator: BarcodeDataCoordinator,
    private val favoritesDataSession: FavoritesDataSession,
    private val favoritesQuerySession: FavoritesQuerySession,
    private val appLogger: AppLogger,
) : ViewModel() {
    private val barcodePersistence = barcodeDataCoordinator.persistence
    val dataState: StateFlow<BarcodeDataState> = favoritesDataSession.dataState
    private val favoritesFacade = FavoritesFacade(
        persistence = barcodePersistence,
        scope = viewModelScope,
        session = favoritesDataSession,
        querySession = favoritesQuerySession,
    )
    private val favoritesStateStore = favoritesFacade.store
    private val favoritesCoordinator = favoritesFacade.mutation
    private val favoritesQueryCoordinator = favoritesFacade.query
    private val favoritesLoadCoordinator = favoritesFacade.load
    private val _persistenceFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val persistenceFailures: SharedFlow<Unit> = _persistenceFailures.asSharedFlow()

    init {
        viewModelScope.launch {
            barcodePersistence.writeFailures.collect { error ->
                appLogger.record("persistence", "write failed", error)
                _persistenceFailures.emit(Unit)
                // Mutations are optimistic in memory. If the queued Room write fails,
                // restore the last durable snapshot instead of leaving stale UI state.
                runCatching { favoritesLoadCoordinator.loadPersistedData() }
                    .onFailure { reloadError ->
                        appLogger.record("persistence", "reload after write failure failed", reloadError)
                    }
            }
        }
    }

    /** 发布只读快照，页面不会直接观察可变集合。 */
    fun publishDataState(isReady: Boolean = dataState.value.isReady) {
        favoritesDataSession.publishDataState(isReady)
    }

    fun persistAllFavorites() {
        favoritesCoordinator.persistAllFavorites()
        publishDataState()
    }

    fun persistItems() {
        barcodePersistence.persistItems(viewModelScope, favoritesStateStore.itemsSnapshot())
        publishDataState()
    }

    suspend fun loadPersistedData() {
        favoritesLoadCoordinator.loadPersistedData()
    }

    private fun refreshFavoritesAfterMutation() {
        favoritesQueryCoordinator.onMutation()
        publishDataState()
    }
}
