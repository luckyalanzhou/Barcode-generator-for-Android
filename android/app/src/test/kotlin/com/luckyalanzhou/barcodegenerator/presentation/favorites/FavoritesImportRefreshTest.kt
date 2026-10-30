package com.luckyalanzhou.barcodegenerator.presentation.favorites

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.StartupBarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeItemViewModel
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.history.HistoryViewModel
import com.luckyalanzhou.barcodegenerator.ui.app.platform.favoritesImportFailureMessage
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FavoritesImportRefreshTest {
    @Test
    fun favoriteGroupDeletionWaitsForRoomAndRestoresStateWhenWriteFails() = runBlocking {
        val originalFailure = IOException("favorite delete failed")
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val item = CodeItem(7L, "code", "CODE_128", favorite = true, folder = "一级")
        val group = FavoriteGroup(3L, "一级", "文件", 1L, mutableListOf(item.id))
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "deleteFavoriteGroups" -> {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args!!.last() as Continuation<Unit>
                    writeStarted.complete(Unit)
                    releaseWrite.invokeOnCompletion { failure ->
                        continuation.resumeWith(
                            if (failure == null) Result.success(Unit) else Result.failure(failure),
                        )
                    }
                    COROUTINE_SUSPENDED
                }
                "loadStartupSnapshot" -> StartupBarcodeSnapshot(
                    items = listOf(item.copy()),
                    groups = listOf(group.copy(itemIds = mutableListOf(item.id))),
                    links = listOf(FavoriteGroupItem(group.id, item.id, 0)),
                    folders = listOf("一级"),
                    hasMoreGroups = false,
                )
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(listOf(item), listOf(group), listOf("一级"))
            publishDataState(isReady = true)
        }
        val viewModel = createViewModel(repository, dataSession, persistence)
        val deletion = async { runCatching { viewModel.deleteFavoriteGroup(group.id) } }

        try {
            withTimeout(5_000) { writeStarted.await() }
            assertFalse("delete confirmation must remain pending until Room replies", deletion.isCompleted)
            releaseWrite.completeExceptionally(originalFailure)

            val reportedFailure = deletion.await().exceptionOrNull()
            assertTrue(reportedFailure is IOException)
            assertEquals(originalFailure.message, reportedFailure?.message)
            assertEquals(listOf(group.id), dataSession.dataState.value.groups.map { it.id })
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun favoriteFolderDeletionWaitsForRoomCommitBeforePublishingRemoval() = runBlocking {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "deleteFavoriteFolder" -> {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args!!.last() as Continuation<Unit>
                    writeStarted.complete(Unit)
                    releaseWrite.invokeOnCompletion { failure ->
                        continuation.resumeWith(
                            if (failure == null) Result.success(Unit) else Result.failure(failure),
                        )
                    }
                    COROUTINE_SUSPENDED
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(emptyList(), emptyList(), listOf("一级"))
            publishDataState(isReady = true)
        }
        val viewModel = createViewModel(repository, dataSession, persistence)
        val deletion = async { viewModel.deleteFavoriteFolder("一级") }

        try {
            withTimeout(5_000) { writeStarted.await() }
            assertFalse("delete confirmation must remain pending until Room replies", deletion.isCompleted)
            assertEquals(listOf("一级"), dataSession.dataState.value.folders)
            releaseWrite.complete(Unit)

            deletion.await()
            assertTrue(dataSession.dataState.value.folders.isEmpty())
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun clearingFavoritesWaitsForAtomicRoomCommitBeforePublishingRemoval() = runBlocking {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val item = CodeItem(7L, "code", "CODE_128", favorite = true, folder = "一级")
        val group = FavoriteGroup(3L, "一级", "文件", 1L, mutableListOf(item.id))
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "clearAllFavorites" -> {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args!!.last() as Continuation<Unit>
                    writeStarted.complete(Unit)
                    releaseWrite.invokeOnCompletion { failure ->
                        continuation.resumeWith(
                            if (failure == null) Result.success(Unit) else Result.failure(failure),
                        )
                    }
                    COROUTINE_SUSPENDED
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(listOf(item), listOf(group), listOf("一级"))
            publishDataState(isReady = true)
        }
        val viewModel = createViewModel(repository, dataSession, persistence)
        val clearing = async { viewModel.clearFavorites() }

        try {
            withTimeout(5_000) { writeStarted.await() }
            assertFalse("clear confirmation must remain pending until the atomic Room transaction replies", clearing.isCompleted)
            assertEquals(listOf(group.id), dataSession.dataState.value.groups.map { it.id })
            releaseWrite.complete(Unit)

            clearing.await()
            assertTrue(dataSession.dataState.value.groups.isEmpty())
            assertTrue(dataSession.dataState.value.folders.isEmpty())
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun clearingFavoritesRestoresSnapshotWhenAtomicRoomTransactionFails() = runBlocking {
        val originalFailure = IOException("clear favorites failed")
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val item = CodeItem(7L, "code", "CODE_128", favorite = true, folder = "一级")
        val group = FavoriteGroup(3L, "一级", "文件", 1L, mutableListOf(item.id))
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "clearAllFavorites" -> {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args!!.last() as Continuation<Unit>
                    writeStarted.complete(Unit)
                    releaseWrite.invokeOnCompletion { failure ->
                        continuation.resumeWith(
                            if (failure == null) Result.success(Unit) else Result.failure(failure),
                        )
                    }
                    COROUTINE_SUSPENDED
                }
                "loadStartupSnapshot" -> StartupBarcodeSnapshot(
                    items = listOf(item.copy()),
                    groups = listOf(group.copy(itemIds = mutableListOf(item.id))),
                    links = listOf(FavoriteGroupItem(group.id, item.id, 0)),
                    folders = listOf("一级"),
                    hasMoreGroups = false,
                )
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(listOf(item), listOf(group), listOf("一级"))
            publishDataState(isReady = true)
        }
        val viewModel = createViewModel(repository, dataSession, persistence)
        val clearing = async { runCatching { viewModel.clearFavorites() } }

        try {
            withTimeout(5_000) { writeStarted.await() }
            assertFalse("clear confirmation must remain pending until Room replies", clearing.isCompleted)
            releaseWrite.completeExceptionally(originalFailure)

            val reportedFailure = clearing.await().exceptionOrNull()
            assertTrue(reportedFailure is IOException)
            assertEquals(originalFailure.message, reportedFailure?.message)
            assertEquals(listOf(group.id), dataSession.dataState.value.groups.map { it.id })
            assertEquals(listOf("一级"), dataSession.dataState.value.folders)
            assertTrue(dataSession.dataState.value.items.single().favorite)
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun folderCreationDoesNotReportSuccessBeforeDatabaseCommit() = runBlocking {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            check(method.name == "saveFavoriteFolders")
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Unit>
            writeStarted.complete(Unit)
            releaseWrite.invokeOnCompletion { failure ->
                continuation.resumeWith(
                    if (failure == null) Result.success(Unit) else Result.failure(failure),
                )
            }
            COROUTINE_SUSPENDED
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession()
        val viewModel = createViewModel(repository, dataSession, persistence)
        val createFolder = async { viewModel.createFavoriteFolder("一级") }

        try {
            withTimeout(5_000) { writeStarted.await() }
            assertFalse("folder creation must remain pending until Room confirms the write", createFolder.isCompleted)
            assertTrue("folder creation must remain unpublished while Room is writing", dataSession.store.foldersSnapshot().isEmpty())
            releaseWrite.complete(Unit)
            assertTrue(createFolder.await())
            assertEquals(listOf("一级"), dataSession.store.foldersSnapshot())
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun folderCreationRejectsPathAlreadyImpliedByFavoriteGroup() = runBlocking {
        val group = FavoriteGroup(9L, "一级/二级", "已存在的收藏", 1L, mutableListOf())
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ -> error("Duplicate folder must not reach persistence: ${method.name}") } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(emptyList(), listOf(group), listOf("一级"), identityGroups = listOf(group))
            publishDataState(isReady = true)
        }
        val viewModel = createViewModel(repository, dataSession, persistence)

        assertTrue(
            "目录树显示收藏组隐含的目录，即使 favorite_folders 没有该目录行",
            "一级/二级" in dataSession.dataState.value.folders + dataSession.dataState.value.groups.map { it.folder },
        )
        assertFalse(viewModel.createFavoriteFolder("一级/二级"))
        assertEquals(listOf("一级"), dataSession.store.foldersSnapshot())
    }

    @Test
    fun folderCreationPreservesWriteFailureWhenRecoveryReusesSameFailure() = runBlocking {
        val storageFailure = IOException("database unavailable")
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        var snapshotReads = 0
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "saveFavoriteFolders" -> {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args!!.last() as Continuation<Unit>
                    writeStarted.complete(Unit)
                    releaseWrite.invokeOnCompletion { failure ->
                        continuation.resumeWith(Result.failure(failure ?: storageFailure))
                    }
                    COROUTINE_SUSPENDED
                }
                "loadStartupSnapshot" -> {
                    snapshotReads++
                    throw storageFailure
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession()
        val viewModel = createViewModel(repository, dataSession, persistence)
        val createFolder = async {
            runCatching { viewModel.createFavoriteFolder("一级") }
        }

        try {
            withTimeout(5_000) { writeStarted.await() }
            releaseWrite.completeExceptionally(storageFailure)
            val result = createFolder.await()

            val reportedFailure = result.exceptionOrNull()
            assertTrue("write failure type must remain visible", reportedFailure is IOException)
            assertEquals("write failure message must remain visible", storageFailure.message, reportedFailure?.message)
            assertEquals("snapshot recovery should keep its single retry", 2, snapshotReads)
            assertTrue(
                "failed folder creation must not leave an optimistic path when snapshot recovery also fails",
                dataSession.store.foldersSnapshot().isEmpty(),
            )
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun failedFolderCreationRestoresDurableStateBeforeReturningForRetry() = runBlocking {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val storageFailure = IOException("disk full")
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "saveFavoriteFolders" -> {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args!!.last() as Continuation<Unit>
                    writeStarted.complete(Unit)
                    releaseWrite.invokeOnCompletion { failure ->
                        continuation.resumeWith(Result.failure(failure ?: storageFailure))
                    }
                    COROUTINE_SUSPENDED
                }
                "loadStartupSnapshot" -> StartupBarcodeSnapshot(
                    items = emptyList(),
                    groups = emptyList(),
                    links = emptyList(),
                    folders = emptyList(),
                    hasMoreGroups = false,
                )
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession()
        val viewModel = createViewModel(repository, dataSession, persistence)
        val createFolder = async { runCatching { viewModel.createFavoriteFolder("一级") } }

        try {
            withTimeout(5_000) { writeStarted.await() }
            releaseWrite.completeExceptionally(storageFailure)
            val result = createFolder.await()

            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals(storageFailure.message, result.exceptionOrNull()?.message)
            assertTrue("failed optimistic folder must be removed before retry", dataSession.store.foldersSnapshot().isEmpty())
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun cancelledFolderCreationStillPublishesCommittedState() = runBlocking {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            check(method.name == "saveFavoriteFolders")
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Unit>
            writeStarted.complete(Unit)
            releaseWrite.invokeOnCompletion { failure ->
                continuation.resumeWith(
                    if (failure == null) Result.success(Unit) else Result.failure(failure),
                )
            }
            COROUTINE_SUSPENDED
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession()
        val viewModel = createViewModel(repository, dataSession, persistence)
        val createFolder = async { viewModel.createFavoriteFolder("一级") }

        try {
            withTimeout(5_000) { writeStarted.await() }
            createFolder.cancel()
            releaseWrite.complete(Unit)
            createFolder.join()
            assertTrue(createFolder.isCancelled)
            persistence.awaitPendingWrites()

            assertEquals(listOf("一级"), dataSession.store.foldersSnapshot())
            assertEquals("a committed mutation must be published even if its Activity caller is cancelled", listOf("一级"), dataSession.dataState.value.folders)
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun startupSnapshotRetriesTransientReadFailure() = runBlocking {
        var snapshotReads = 0
        val snapshot = StartupBarcodeSnapshot(
            items = emptyList(),
            groups = emptyList(),
            links = emptyList(),
            folders = emptyList(),
            hasMoreGroups = false,
        )
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            check(method.name == "loadStartupSnapshot") { "Unexpected repository operation: ${method.name}" }
            snapshotReads++
            if (snapshotReads == 1) throw IllegalStateException("transient startup snapshot failure")
            snapshot
        } as BarcodeRepository
        val dataSession = LibraryDataSession()
        val coordinator = LibraryDataCoordinator(
            BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
                override suspend fun migrateIfNeeded() = Unit
            }),
            dataSession,
            AppLogger { _, _, _ -> },
        )

        val result = runCatching { coordinator.loadPersistedData() }

        assertEquals(2, snapshotReads)
        assertTrue("transient startup read should recover", result.isSuccess)
        assertTrue(dataSession.dataState.value.isReady)
    }

    @Test
    fun backupImportWaitsForPendingFavoriteMutationBeforeWriting() = runBlocking {
        val renameStarted = CompletableDeferred<Unit>()
        val releaseRename = CompletableDeferred<Unit>()
        val importStarted = CompletableDeferred<Unit>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "renameFavoriteFolder" -> {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args!!.last() as Continuation<Unit>
                    renameStarted.complete(Unit)
                    releaseRename.invokeOnCompletion { failure ->
                        continuation.resumeWith(
                            if (failure == null) Result.success(Unit) else Result.failure(failure),
                        )
                    }
                    COROUTINE_SUSPENDED
                }
                "loadStartupSnapshot" -> StartupBarcodeSnapshot(
                    items = emptyList(),
                    groups = emptyList(),
                    links = emptyList(),
                    folders = emptyList(),
                    hasMoreGroups = false,
                )
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(emptyList(), emptyList(), listOf("旧目录"))
            publishDataState(isReady = true)
        }
        val logger = AppLogger { _, _, _ -> }
        val backupRepository = object : FavoritesBackupRepository {
            override suspend fun export(output: OutputStream) = Unit
            override fun restore(bytes: ByteArray) = InterchangeBackup(emptyList(), emptyList())
            override suspend fun inspectImport(backup: InterchangeBackup) =
                FavoritesImportConflictSummary(emptyList(), emptyList())
            override suspend fun import(backup: InterchangeBackup, overwriteConflicts: Boolean): Pair<Int, Int> {
                importStarted.complete(Unit)
                return 0 to 0
            }
        }
        val viewModel = FavoritesViewModel(
            dataSession = dataSession,
            querySession = FavoritesQuerySession(repository, dataSession, persistence),
            barcodeDataCoordinator = BarcodeDataCoordinator(repository, backupRepository, persistence),
            libraryDataCoordinator = LibraryDataCoordinator(persistence, dataSession, logger),
            persistence = persistence,
            appLogger = logger,
        )
        val rename = async { viewModel.renameFavoriteFolder("旧目录", "新目录") }

        try {
            withTimeout(5_000) { renameStarted.await() }
            val import = async { viewModel.importFavorites(InterchangeBackup(emptyList(), emptyList()), overwriteConflicts = true) }
            yield()

            assertFalse("backup import must not overlap an in-flight favorite mutation", importStarted.isCompleted)
            releaseRename.complete(Unit)
            assertTrue(rename.await())
            assertEquals(0 to 0, import.await())
            assertTrue(importStarted.isCompleted)
        } finally {
            releaseRename.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun barcodeItemEditWaitsForImportRefreshAndPersistsAgainstTheFreshSnapshot() = runBlocking {
        val item = CodeItem(1L, "before-import", "Code 128-B", favorite = true, folder = "目录")
        val group = FavoriteGroup(2L, "目录", "文件", 1L, mutableListOf(item.id))
        val importStarted = CompletableDeferred<Unit>()
        val releaseImport = CompletableDeferred<Unit>()
        val mutationPersisted = CompletableDeferred<BarcodeSnapshot>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "loadStartupSnapshot" -> StartupBarcodeSnapshot(
                    items = listOf(item.copy()),
                    groups = listOf(group.copy(itemIds = mutableListOf(item.id))),
                    links = listOf(FavoriteGroupItem(group.id, item.id, 0)),
                    folders = listOf("目录"),
                    hasMoreGroups = false,
                )
                "applyFavoritesMutation" -> {
                    mutationPersisted.complete(args!![0] as BarcodeSnapshot)
                    Unit
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(listOf(item), listOf(group), listOf("目录"))
            publishDataState(isReady = true)
        }
        val logger = AppLogger { _, _, _ -> }
        val backupRepository = object : FavoritesBackupRepository {
            override suspend fun export(output: OutputStream) = Unit
            override fun restore(bytes: ByteArray) = InterchangeBackup(emptyList(), emptyList())
            override suspend fun inspectImport(backup: InterchangeBackup) =
                FavoritesImportConflictSummary(emptyList(), emptyList())
            override suspend fun import(backup: InterchangeBackup, overwriteConflicts: Boolean): Pair<Int, Int> {
                importStarted.complete(Unit)
                releaseImport.await()
                return 0 to 0
            }
        }
        val querySession = FavoritesQuerySession(repository, dataSession, persistence)
        val viewModel = FavoritesViewModel(
            dataSession = dataSession,
            querySession = querySession,
            barcodeDataCoordinator = BarcodeDataCoordinator(repository, backupRepository, persistence),
            libraryDataCoordinator = LibraryDataCoordinator(persistence, dataSession, logger),
            persistence = persistence,
            appLogger = logger,
        )
        val itemViewModel = BarcodeItemViewModel(dataSession, querySession, persistence, logger)
        val itemViewModelStore = ViewModelStore().apply { put("barcode-item", itemViewModel) }
        val importing = async { viewModel.importFavorites(InterchangeBackup(emptyList(), emptyList())) }

        try {
            withTimeout(5_000) { importStarted.await() }
            itemViewModel.updateBarcodeItem(item.id, "after-import", "Code 128-B")
            yield()
            assertEquals("before-import", dataSession.store.itemsSnapshot().single().text)
            // 复现 Activity 最终销毁：已经接受的排队编辑不能随 ViewModel scope 一起丢弃。
            itemViewModelStore.clear()

            releaseImport.complete(Unit)
            assertEquals(0 to 0, importing.await())
            val persisted = withTimeout(5_000) { mutationPersisted.await() }
            assertEquals("after-import", persisted.items.single().text)
            assertEquals(listOf(group.id), persisted.groups.map { it.id })

            val idleItemViewModel = BarcodeItemViewModel(dataSession, querySession, persistence, logger)
            idleItemViewModel.updateBarcodeItem(item.id, "after-import-idle", "Code 128-B")
            assertEquals("after-import-idle", dataSession.store.itemsSnapshot().single().text)
        } finally {
            releaseImport.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun historyBatchDeleteWaitsForLibraryMutationAndPersistsTheFreshSnapshot() = runBlocking {
        val oldItem = CodeItem(1L, "old-item", "Code 128-B", favorite = false, inHistory = true)
        val importedItem = CodeItem(
            1L,
            "imported-item",
            "Code 128-B",
            favorite = true,
            folder = "导入目录",
            inHistory = true,
        )
        val persistedItems = CompletableDeferred<List<CodeItem>>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "saveItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    persistedItems.complete((args!![0] as List<CodeItem>).map(CodeItem::copy))
                    Unit
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(listOf(oldItem), emptyList(), emptyList())
            publishDataState(isReady = true)
        }
        val viewModel = HistoryViewModel(dataSession, persistence)

        persistence.withLibraryMutation {
            viewModel.deleteHistoryBatch(listOf(oldItem))
            // 在导入持有共享变更锁时运行 ViewModel 操作，模拟并发写入等待。
            repeat(20) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                yield()
            }

            assertTrue(
                "history deletion must wait instead of mutating the pre-import shared snapshot",
                dataSession.store.itemsSnapshot().single().inHistory,
            )
            dataSession.store.replace(listOf(importedItem), emptyList(), listOf("导入目录"))
            dataSession.publishDataState(isReady = true)
        }

        val saved = withTimeout(5_000) { persistedItems.await() }
        assertEquals("imported-item", saved.single().text)
        assertTrue("the imported favorite flag must survive history deletion", saved.single().favorite)
        assertEquals("导入目录", saved.single().folder)
        assertFalse("the requested history deletion must still be applied", saved.single().inHistory)
        persistence.awaitPendingWrites()
    }

    @Test
    fun clearHistoryWaitsForLibraryMutationAndAppliesToTheFreshSnapshot() = runBlocking {
        val oldItem = CodeItem(1L, "old-item", "Code 128-B", favorite = false, inHistory = true)
        val importedItem = CodeItem(
            1L,
            "imported-item",
            "Code 128-B",
            favorite = true,
            folder = "导入目录",
            inHistory = true,
        )
        val clearStarted = CompletableDeferred<Unit>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "clearHistory" -> {
                    clearStarted.complete(Unit)
                    Unit
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(listOf(oldItem), emptyList(), emptyList())
            publishDataState(isReady = true)
        }
        val viewModel = HistoryViewModel(dataSession, persistence)

        persistence.withLibraryMutation {
            viewModel.clearHistoryAndPersist()
            repeat(20) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                yield()
            }

            assertTrue(
                "clear history must not edit the pre-import shared snapshot",
                dataSession.store.itemsSnapshot().single().inHistory,
            )
            dataSession.store.replace(listOf(importedItem), emptyList(), listOf("导入目录"))
            dataSession.publishDataState(isReady = true)
        }

        withTimeout(5_000) { clearStarted.await() }
        persistence.awaitPendingWrites()
        val refreshed = dataSession.store.itemsSnapshot().single()
        assertEquals("imported-item", refreshed.text)
        assertTrue("imported favorite state must remain intact", refreshed.favorite)
        assertEquals("导入目录", refreshed.folder)
        assertFalse("clear history must apply after import refresh", refreshed.inHistory)
    }

    @Test
    fun committedImportRetriesTransientSnapshotReadFailureAndReturnsSuccess() = runBlocking {
        val importedItem = CodeItem(40L, "imported-item", "QR_CODE", 3L, favorite = true, folder = "导入目录")
        val importedGroup = FavoriteGroup(20L, "导入目录", "导入文件", 3L, mutableListOf(importedItem.id))
        val snapshot = StartupBarcodeSnapshot(
            items = listOf(importedItem),
            groups = listOf(importedGroup),
            links = listOf(FavoriteGroupItem(importedGroup.id, importedItem.id, 0)),
            folders = listOf("导入目录"),
            hasMoreGroups = false,
        )
        var snapshotReads = 0
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            check(method.name == "loadStartupSnapshot") { "Unexpected repository operation: ${method.name}" }
            snapshotReads++
            if (snapshotReads == 1) throw IllegalStateException("transient snapshot read failure")
            snapshot
        } as BarcodeRepository
        var importCalls = 0
        val backupRepository = object : FavoritesBackupRepository {
            override suspend fun export(output: OutputStream) = Unit
            override fun restore(bytes: ByteArray) = InterchangeBackup(emptyList(), emptyList())
            override suspend fun inspectImport(backup: InterchangeBackup) =
                FavoritesImportConflictSummary(emptyList(), emptyList())

            override suspend fun import(backup: InterchangeBackup, overwriteConflicts: Boolean): Pair<Int, Int> {
                importCalls++
                return 1 to 1
            }
        }
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(
                newItems = listOf(CodeItem(1L, "old-item", "QR_CODE", 1L, favorite = true, folder = "旧目录")),
                newGroups = listOf(FavoriteGroup(1L, "旧目录", "旧文件", 1L, mutableListOf(1L))),
                newFolders = listOf("旧目录"),
            )
            publishDataState(isReady = true)
        }
        val viewModel = FavoritesViewModel(
            dataSession = dataSession,
            querySession = FavoritesQuerySession(repository, dataSession, persistence),
            barcodeDataCoordinator = BarcodeDataCoordinator(repository, backupRepository, persistence),
            libraryDataCoordinator = LibraryDataCoordinator(
                persistence,
                dataSession,
                AppLogger { _, _, _ -> },
            ),
            persistence = persistence,
            appLogger = AppLogger { _, _, _ -> },
        )

        val result = runCatching {
            viewModel.importFavorites(InterchangeBackup(emptyList(), emptyList()))
        }

        assertEquals("backup import transaction must run exactly once", 1, importCalls)
        assertEquals(2, snapshotReads)
        val counts = result.getOrNull()
            ?: throw AssertionError("A transient post-commit read failure should be retried", result.exceptionOrNull())
        assertEquals(1 to 1, counts)
        assertTrue(dataSession.dataState.value.isReady)
        assertEquals(listOf("导入文件"), dataSession.dataState.value.groups.map { it.name })
        assertEquals(listOf("imported-item"), dataSession.dataState.value.items.map { it.text })
        assertEquals(1L, viewModel.snapshotMetadata.value?.generation)
    }

    @Test
    fun persistentStartupSnapshotRetriesOnceAndLeavesDataNotReady() = runBlocking {
        var snapshotReads = 0
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            check(method.name == "loadStartupSnapshot") { "Unexpected repository operation: ${method.name}" }
            snapshotReads++
            throw IllegalStateException("persistent snapshot read failure")
        } as BarcodeRepository
        val dataSession = LibraryDataSession()
        val coordinator = LibraryDataCoordinator(
            BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
                override suspend fun migrateIfNeeded() = Unit
            }),
            dataSession,
            AppLogger { _, _, _ -> },
        )

        val error = runCatching { coordinator.loadPersistedData() }.exceptionOrNull()

        assertEquals(2, snapshotReads)
        assertFalse(dataSession.dataState.value.isReady)
        assertTrue(error is IllegalStateException)
        assertEquals(1, error?.suppressed?.size)
    }

    @Test
    fun postImportRefreshFailureMessageReportsCommittedCounts() {
        val committedError = FavoritesImportRefreshException(5, 2, IllegalStateException("persistent read failure"))
        assertEquals(5, committedError.importedItemCount)
        assertEquals(2, committedError.importedGroupCount)
        val message = favoritesImportFailureMessage(committedError)
        assertTrue(message.contains("数据库已写入 2 个收藏、5 条码"))
        assertTrue(message.contains("重启应用后查看"))
        assertTrue(!message.startsWith("收藏导入失败"))
    }

    private fun createViewModel(
        repository: BarcodeRepository,
        dataSession: LibraryDataSession,
        persistence: BarcodePersistenceCoordinator,
    ): FavoritesViewModel {
        val logger = AppLogger { _, _, _ -> }
        return FavoritesViewModel(
            dataSession = dataSession,
            querySession = FavoritesQuerySession(repository, dataSession, persistence),
            barcodeDataCoordinator = BarcodeDataCoordinator(
                repository,
                object : FavoritesBackupRepository {
                    override suspend fun export(output: OutputStream) = Unit
                    override fun restore(bytes: ByteArray) = InterchangeBackup(emptyList(), emptyList())
                    override suspend fun inspectImport(backup: InterchangeBackup) =
                        FavoritesImportConflictSummary(emptyList(), emptyList())
                    override suspend fun import(backup: InterchangeBackup, overwriteConflicts: Boolean) = 0 to 0
                },
                persistence,
            ),
            libraryDataCoordinator = LibraryDataCoordinator(persistence, dataSession, logger),
            persistence = persistence,
            appLogger = logger,
        )
    }
}
