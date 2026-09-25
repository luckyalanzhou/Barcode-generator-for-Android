package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Loads and publishes the durable library snapshot independently of any one feature. */
@ActivityRetainedScoped
class LibraryDataCoordinator @Inject constructor(
    private val persistence: BarcodePersistenceCoordinator,
    private val session: LibraryDataSession,
    private val logger: AppLogger,
) {
    private val loadMutex = Mutex()

    suspend fun loadPersistedData() = loadMutex.withLock {
        session.publishDataState(isReady = false)
        val loaded = persistence.load()
        logger.record(
            "library",
            "snapshot loaded groups=${loaded.groups.size} items=${loaded.items.size} folders=${loaded.folders.size} hasMore=${loaded.hasMoreGroups}",
            null,
        )
        session.store.replace(loaded.items, loaded.groups, loaded.folders, loaded.identityGroups)
        val cursor = loaded.groups.lastOrNull()?.let { FavoriteGroupPageCursor(it.savedAt, it.id) }
        session.publishLoadedSnapshot(cursor, loaded.hasMoreGroups)
        session.publishDataState(isReady = true)
    }
}
