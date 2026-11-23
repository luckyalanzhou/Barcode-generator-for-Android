package com.luckyalanzhou.barcodegenerator.data

import androidx.room.Room
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class HistoryAndBackupRegressionTest {
    @Test
    fun startupCompletesBoundaryBatchAndDeletingItKeepsItsFavorite() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BarcodeDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomBarcodeRepository(database)
            val old = listOf(
                CodeItem(1, "old-one", "Code 128-B", 1, historyBatchId = "old"),
                CodeItem(2, "old-two", "Code 128-B", 1, historyBatchId = "old"),
            )
            val recent = (3L..501L).map { CodeItem(it, "new-$it", "Code 128-B", it, historyBatchId = "new-$it") }
            repository.saveAll(BarcodeSnapshot(
                old + recent,
                listOf(FavoriteGroup(1, "QA", "kept", 1, mutableListOf(1))),
                listOf(FavoriteGroupItem(1, 1, 0)),
                listOf("QA"),
            ))

            assertEquals(listOf(2L, 1L), repository.loadStartupSnapshot().items.filter { it.historyBatchId == "old" }.map { it.id })
            repository.deleteHistoryBatch("old", 1)
            assertEquals(emptyList<CodeItem>(), repository.loadStartupSnapshot().items.filter { it.historyBatchId == "old" })
            assertEquals(listOf(1L), repository.loadFavoriteGroupContent(1)?.items?.map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun historySearchSourceIncludesOlderBatchesAndExcludesDeletedHistory() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BarcodeDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomBarcodeRepository(database)
            val all = (1L..502L).map { id ->
                CodeItem(id, "QA-$id", "Code 128-B", id, historyBatchId = "batch-$id")
            }
            repository.saveAll(BarcodeSnapshot(all, emptyList(), emptyList(), emptyList()))
            assertEquals(500, repository.loadStartupSnapshot().items.size)
            assertEquals(502, repository.observeHistoryItems().first().size)
            repository.deleteHistoryBatch("batch-1", 1)
            val afterDelete = repository.observeHistoryItems().first()
            assertEquals(501, afterDelete.size)
            assertEquals(false, afterDelete.any { it.id == 1L })
        } finally {
            database.close()
        }
    }

    @Test
    fun backupPreservesOriginalNamesOfEmptyFolders() {
        val original = listOf("QA:2026", "QA?2026", "CON", "CON/Empty")
        val archive = ByteArrayOutputStream()
        FavoritesTransferManager.export(archive, BarcodeSnapshot(emptyList(), emptyList(), emptyList(), original))

        assertEquals(original, FavoritesTransferManager.restore(archive.toByteArray()).folders)
    }
}
