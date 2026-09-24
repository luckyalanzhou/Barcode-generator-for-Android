package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import kotlinx.coroutines.CoroutineScope

/** 收藏领域的组合边界；具体查询、分页、变更仍由各自 Coordinator 负责。 */
internal class FavoritesFacade(
    repository: BarcodeRepository,
    persistence: BarcodePersistenceCoordinator,
    scope: CoroutineScope,
    private val session: FavoritesDataSession,
) {
    val store = session.store
    val searchStore = session.searchStore
    val mutation = FavoritesMutationCoordinator(store, persistence, scope)
    val query = FavoritesQueryCoordinator(repository, store, searchStore)
    val history = com.luckyalanzhou.barcodegenerator.presentation.history.HistoryCoordinator(
        store = store,
        persistItems = { items -> persistence.persistItems(scope, items) },
    )
    val load = FavoritesLoadCoordinator(
        persistence = persistence,
        store = store,
        query = query,
        publish = session::publishDataState,
        publishSearch = { session.publishSearchState(query) },
    )

    fun publishSearchState() = session.publishSearchState(query)
}
