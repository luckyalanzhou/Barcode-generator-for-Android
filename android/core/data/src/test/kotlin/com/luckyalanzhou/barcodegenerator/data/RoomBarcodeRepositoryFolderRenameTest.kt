package com.luckyalanzhou.barcodegenerator.data

import androidx.room.Room
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 通过仓储接口验证重命名后的收藏排序能从 Room 重新读取恢复。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RoomBarcodeRepositoryFolderRenameTest {
    @Test
    fun renamingFolderPersistsRecencyForRootAndNestedGroups() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            BarcodeDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val repository: BarcodeRepository = RoomBarcodeRepository(database)
            repository.saveFavoriteGroups(
                groups = listOf(
                    FavoriteGroup(1L, "Root", "root-file", 1L, mutableListOf()),
                    FavoriteGroup(2L, "Root/Child", "child-file", 2L, mutableListOf()),
                    FavoriteGroup(3L, "Other", "other-file", 3L, mutableListOf()),
                ),
                links = emptyList(),
            )

            repository.renameFavoriteFolder("Root", "Renamed", savedAt = 10L)

            val reloadedGroups = repository.loadGroups()
            assertEquals(listOf(2L, 1L, 3L), reloadedGroups.map(FavoriteGroup::id))
            assertEquals(listOf("Renamed/Child", "Renamed", "Other"), reloadedGroups.map(FavoriteGroup::folder))
            assertEquals(reloadedGroups[0].savedAt, reloadedGroups[1].savedAt)
            assertTrue(reloadedGroups[1].savedAt > reloadedGroups[2].savedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun renamingNestedFolderPersistsItsSubtreeWithoutRenamingSharedPrefixes() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            BarcodeDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val repository: BarcodeRepository = RoomBarcodeRepository(database)
            repository.saveFavoriteGroups(
                groups = listOf(
                    FavoriteGroup(1L, "Root/Child", "nested", 1L, mutableListOf()),
                    FavoriteGroup(2L, "Root/Child/Grandchild", "descendant", 2L, mutableListOf()),
                    FavoriteGroup(3L, "Root/Childish", "prefix-neighbor", 3L, mutableListOf()),
                    FavoriteGroup(4L, "Root/Sibling", "sibling", 4L, mutableListOf()),
                    FavoriteGroup(5L, "Other", "other", 5L, mutableListOf()),
                ),
                links = emptyList(),
            )
            repository.saveFavoriteFolders(
                listOf("Root", "Root/Child", "Root/Child/Grandchild", "Root/Childish", "Root/Sibling", "Other"),
            )

            repository.renameFavoriteFolder("Root/Child", "Root/Renamed", savedAt = 20L)

            assertEquals(
                mapOf(
                    1L to "Root/Renamed",
                    2L to "Root/Renamed/Grandchild",
                    3L to "Root/Childish",
                    4L to "Root/Sibling",
                    5L to "Other",
                ),
                repository.loadGroups().associate { it.id to it.folder },
            )
            assertEquals(
                setOf("Root", "Root/Renamed", "Root/Renamed/Grandchild", "Root/Childish", "Root/Sibling", "Other"),
                repository.loadFolders().toSet(),
            )
            assertEquals(
                setOf(20L),
                repository.loadGroups().filter { it.id == 1L || it.id == 2L }.map { it.savedAt }.toSet(),
            )
        } finally {
            database.close()
        }
    }
}
