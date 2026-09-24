package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import com.luckyalanzhou.barcodegenerator.domain.AppLogger

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor

/** Loads favorite/history data and owns the paging refresh boundary. */
internal class FavoritesLoadCoordinator(
    private val persistence: BarcodePersistenceCoordinator,
    private val logger: AppLogger,
    private val store: LibraryStateStore,
    private val query: FavoritesQueryCoordinator,
    private val publish: (Boolean) -> Unit,
    private val publishSearch: () -> Unit,
) {
    suspend fun loadPersistedData() {
        publish(false)
        val loaded = persistence.load()
        logger.record(
            "favorites",
            "startup loaded groups=${loaded.groups.size} items=${loaded.items.size} folders=${loaded.folders.size} hasMore=${loaded.hasMoreGroups}",
            null,
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
