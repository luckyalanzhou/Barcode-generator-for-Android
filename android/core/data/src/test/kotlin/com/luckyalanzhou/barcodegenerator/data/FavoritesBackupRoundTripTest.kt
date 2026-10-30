package com.luckyalanzhou.barcodegenerator.data

import androidx.room.Room
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 通过备份仓储与 Room 公共边界验证非空收藏 ZIP 的完整往返。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoritesBackupRoundTripTest {
    @Test
    fun nonEmptyBackupPreservesFolderOrderRepeatedTextAndFormats() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(SOURCE_DATABASE)
        context.deleteDatabase(DESTINATION_DATABASE)
        var sourceDatabase = openDatabase(SOURCE_DATABASE)
        var destinationDatabase = openDatabase(DESTINATION_DATABASE)

        try {
            val sourceRepository = RoomBarcodeRepository(sourceDatabase)
            val destinationRepository = RoomBarcodeRepository(destinationDatabase)
            sourceRepository.saveAll(
                BarcodeSnapshot(
                    items = listOf(
                        CodeItem(31L, "REPEATED", "QR Code", 10L, true, "一级/二级", false),
                        CodeItem(47L, "REPEATED", "Code 128-B", 20L, true, "一级/二级", false),
                    ),
                    groups = listOf(FavoriteGroup(8L, "一级/二级", "round-trip", 700L, mutableListOf(47L, 31L))),
                    links = listOf(FavoriteGroupItem(8L, 47L, 0), FavoriteGroupItem(8L, 31L, 1)),
                    folders = listOf("一级", "一级/二级"),
                ),
            )

            val exported = ByteArrayOutputStream().also { output ->
                FavoritesBackupUseCase(sourceRepository).export(output)
            }.toByteArray()
            val destinationBackup = FavoritesBackupUseCase(destinationRepository)
            val restored = destinationBackup.restore(exported)
            val importCounts = destinationBackup.import(restored)
            destinationDatabase.close()
            destinationDatabase = openDatabase(DESTINATION_DATABASE)
            val imported = RoomBarcodeRepository(destinationDatabase).loadSnapshot()

            assertEquals(2 to 1, importCounts)
            assertEquals(listOf("一级", "一级/二级"), imported.folders.sortedBy { it.count { char -> char == '/' } })
            val group = imported.groups.single()
            assertEquals("一级/二级", group.folder)
            assertEquals("round-trip", group.name)
            assertEquals(700L, group.savedAt)

            val itemsById = imported.items.associateBy(CodeItem::id)
            val orderedLinks = imported.links.filter { it.groupId == group.id }.sortedBy(FavoriteGroupItem::position)
            assertEquals(listOf(0, 1), orderedLinks.map(FavoriteGroupItem::position))
            assertEquals(listOf("REPEATED", "REPEATED"), orderedLinks.map { itemsById.getValue(it.itemId).text })
            assertEquals(listOf("Code 128-B", "QR Code"), orderedLinks.map { itemsById.getValue(it.itemId).format })
            assertEquals(2, orderedLinks.map(FavoriteGroupItem::itemId).distinct().size)
            assertTrue(exported.isNotEmpty())
        } finally {
            destinationDatabase.close()
            sourceDatabase.close()
            context.deleteDatabase(DESTINATION_DATABASE)
            context.deleteDatabase(SOURCE_DATABASE)
        }
    }

    private fun openDatabase(name: String): BarcodeDatabase =
        Room.databaseBuilder(RuntimeEnvironment.getApplication(), BarcodeDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val SOURCE_DATABASE = "favorites-backup-source-test.db"
        const val DESTINATION_DATABASE = "favorites-backup-destination-test.db"
    }
}
