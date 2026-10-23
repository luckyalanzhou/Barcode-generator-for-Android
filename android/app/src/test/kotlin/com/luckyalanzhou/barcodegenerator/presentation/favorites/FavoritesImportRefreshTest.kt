package com.luckyalanzhou.barcodegenerator.presentation.favorites

import android.app.Application
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.StartupBarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.ui.app.platform.favoritesImportFailureMessage
import java.io.OutputStream
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FavoritesImportRefreshTest {
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
}
