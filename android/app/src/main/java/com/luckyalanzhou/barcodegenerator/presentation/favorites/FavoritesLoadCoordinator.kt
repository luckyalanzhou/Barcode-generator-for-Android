package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog

/** Loads favorite/history data and owns the paging refresh boundary. */
internal class FavoritesLoadCoordinator(
    private val persistence: BarcodePersistenceCoordinator,
    private val store: FavoritesStateStore,
    private val query: FavoritesQueryCoordinator,
    private val publish: (Boolean) -> Unit,
    private val publishSearch: () -> Unit,
) {
    suspend fun loadPersistedData() {
        publish(false)
        val loaded = persistence.load()
        DebugLog.record(
            "favorites",
            "startup loaded groups=${loaded.groups.size} items=${loaded.items.size} folders=${loaded.folders.size} hasMore=${loaded.hasMoreGroups}",
        )
        store.replace(loaded.items, loaded.groups, loaded.folders)
        query.resetPaging(
            loaded.groups.lastOrNull()?.let { FavoriteGroupPageCursor(it.savedAt, it.id) },
            loaded.hasMoreGroups,
        )
        publish(true)
        publishSearch()
    }
}
