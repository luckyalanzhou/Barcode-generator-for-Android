package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

/** 独立于具体功能加载并发布持久化收藏库快照。 */
@ActivityRetainedScoped
class LibraryDataCoordinator @Inject constructor(
    private val persistence: BarcodePersistenceCoordinator,
    private val session: LibraryDataSession,
    private val logger: AppLogger,
) {
    private val loadMutex = Mutex()

    suspend fun loadPersistedData() = loadMutex.withLock {
        session.publishDataState(isReady = false, loadFailed = false)
        try {
            val loaded = persistence.loadWithSnapshotRetry { error ->
                logger.recordSafely("library", "snapshot read failed; retrying once", error, error)
            }
            logger.recordSafely(
                "library",
                "snapshot loaded groups=${loaded.groups.size} items=${loaded.items.size} folders=${loaded.folders.size} hasMore=${loaded.hasMoreGroups}",
                null,
            )
            session.store.replace(loaded.items, loaded.groups, loaded.folders, loaded.identityGroups)
            val cursor = loaded.groups.lastOrNull()?.let { FavoriteGroupPageCursor(it.savedAt, it.id) }
            session.publishLoadedSnapshot(cursor, loaded.hasMoreGroups)
            session.publishDataState(isReady = true, loadFailed = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            session.publishDataState(isReady = false, loadFailed = true)
            throw error
        }
    }
}
