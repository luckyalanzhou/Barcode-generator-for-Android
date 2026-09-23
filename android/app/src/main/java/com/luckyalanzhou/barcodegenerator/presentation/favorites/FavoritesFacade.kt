package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeDataStateCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/** 收藏领域的组合边界；具体查询、分页、变更仍由各自 Coordinator 负责。 */
internal class FavoritesFacade(
    repository: BarcodeRepository,
    persistence: BarcodePersistenceCoordinator,
    scope: CoroutineScope,
    private val dataState: MutableStateFlow<BarcodeDataState>,
    private val searchState: MutableStateFlow<BarcodeDataState>,
) {
    val store = FavoritesStateStore()
    val searchStore = FavoritesStateStore()
    val mutation = FavoritesMutationCoordinator(store, persistence, scope)
    val query = FavoritesQueryCoordinator(repository, store, searchStore)
    val history = com.luckyalanzhou.barcodegenerator.presentation.history.HistoryCoordinator(
        store = store,
        persistItems = { items -> persistence.persistItems(scope, items) },
    )
    val pageState = FavoritesPageStateCoordinator()
    val dataStateCoordinator = BarcodeDataStateCoordinator(store, dataState)
    val load = FavoritesLoadCoordinator(
        persistence = persistence,
        store = store,
        query = query,
        publish = { isReady -> dataStateCoordinator.publish(isReady) },
        publishSearch = {
            searchState.value = query.searchSnapshot(dataState.value.isReady).copy(
                folders = dataState.value.folders,
            )
        },
    )

    fun publishSearchState() {
        searchState.value = query.searchSnapshot(dataState.value.isReady).copy(
            folders = dataState.value.folders,
        )
    }
}
