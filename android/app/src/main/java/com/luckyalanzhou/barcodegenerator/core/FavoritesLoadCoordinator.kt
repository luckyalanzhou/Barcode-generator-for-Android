package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor

/** Loads favorite/history data and owns the paging refresh boundary. */
internal class FavoritesLoadCoordinator(
    private val persistence: BarcodePersistenceCoordinator,
    private val store: FavoritesStateStore,
    private val query: FavoritesQueryCoordinator,
    private val publish: (Boolean) -> Unit,
) {
    suspend fun loadPersistedData() {
        publish(false)
        val loaded = persistence.load()
        store.replace(loaded.items, loaded.groups, loaded.folders)
        query.resetPaging(
            loaded.groups.lastOrNull()?.let { FavoriteGroupPageCursor(it.savedAt, it.id) },
            loaded.hasMoreGroups,
        )
        publish(true)
    }

    suspend fun loadMoreFavoriteGroups() {
        if (query.loadMore()) publish(true)
    }
}
