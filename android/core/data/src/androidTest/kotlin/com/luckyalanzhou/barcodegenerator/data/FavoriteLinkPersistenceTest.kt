package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteLinkPersistenceTest {
    @Test
    fun deletingWildcardNamedFolderDoesNotDeleteOtherFoldersOrFavoriteLinks() = runBlocking {
        withFolderRepository(
            listOf("%", "%/child", "other/child", "A_B/child", "A1B/child"),
        ) { database, repository ->
            repository.deleteFavoriteFolder("%")

            assertEquals(listOf(3L, 4L, 5L), repository.loadGroups().sortedBy { it.id }.map { it.id })
            assertEquals(listOf(3L, 4L, 5L), repository.loadGroupItems().map { it.groupId }.sorted())
            assertEquals(listOf("A1B/child", "other/child", "A_B/child"), database.barcodeDao().loadFolders().map { it.name })
            val itemsById = repository.loadItems().associateBy { it.id }
            assertTrue(itemsById.filterKeys { it in 1L..2L }.values.none { it.favorite })
            assertTrue(itemsById.filterKeys { it in 3L..5L }.values.all { it.favorite })
        }
    }

    @Test
    fun renamingFolderUsesExactCaseAndLiteralWildcardCharacters() = runBlocking {
        withFolderRepository(
            listOf("A_B", "A_B/child", "A1B/child", "Work/child", "work/child"),
        ) { database, repository ->
            repository.renameFavoriteFolder("A_B", "renamed")
            repository.renameFavoriteFolder("Work", "WorkRenamed")

            assertEquals(
                listOf("renamed", "renamed/child", "A1B/child", "WorkRenamed/child", "work/child"),
                repository.loadGroups().sortedBy { it.id }.map { it.folder },
            )
            assertEquals(
                listOf("A1B/child", "WorkRenamed/child", "renamed", "renamed/child", "work/child"),
                database.barcodeDao().loadFolders().map { it.name },
            )
        }
    }

    @Test
    fun savingExistingFavoriteItemPreservesItsGroupLink() = runBlocking {
        withSeededRepository { database, repository ->
            repository.saveItems(listOf(favoriteItem(text = "updated barcode")))

            assertEquals(listOf(1L), repository.loadGroupItemIds(10L))
            assertEquals("updated barcode", repository.loadFavoriteGroupContent(10L)?.items?.single()?.text)
        }
    }

    @Test
    fun updatingExistingGroupWithoutReplacingLinksPreservesItsGroupLink() = runBlocking {
        withSeededRepository { _, repository ->
            repository.applyFavoritesMutation(
                BarcodeSnapshot(
                    items = listOf(favoriteItem(text = "updated barcode")),
                    groups = listOf(FavoriteGroup(10L, "folder", "renamed group", 100L, mutableListOf())),
                    links = emptyList(),
                    folders = listOf("folder"),
                ),
            )

            assertEquals(listOf(1L), repository.loadGroupItemIds(10L))
            assertEquals("updated barcode", repository.loadFavoriteGroupContent(10L)?.items?.single()?.text)
        }
    }

    private suspend fun withSeededRepository(
        test: suspend (BarcodeDatabase, RoomBarcodeRepository) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BarcodeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.barcodeDao()
            dao.upsertItems(listOf(favoriteItem(text = "original barcode").toEntity()))
            dao.upsertGroups(listOf(FavoriteGroupEntity(10L, "folder", "group", 100L)))
            dao.saveGroupItems(listOf(FavoriteGroupItemEntity(10L, 1L)))
            test(database, RoomBarcodeRepository(database))
        } finally {
            database.close()
        }
    }

    private suspend fun withFolderRepository(
        folders: List<String>,
        test: suspend (BarcodeDatabase, RoomBarcodeRepository) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BarcodeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.barcodeDao()
            val groups = folders.mapIndexed { index, folder ->
                val id = index + 1L
                FavoriteGroupEntity(id, folder, "group-$id", id)
            }
            dao.upsertItems(groups.map { group ->
                favoriteItem(id = group.id, folder = group.folder).toEntity()
            })
            dao.upsertGroups(groups)
            dao.saveGroupItems(groups.map { FavoriteGroupItemEntity(it.id, it.id) })
            dao.saveFolders(folders.distinct().map(::FavoriteFolderEntity))
            test(database, RoomBarcodeRepository(database))
        } finally {
            database.close()
        }
    }

    private fun favoriteItem(id: Long = 1L, text: String = "original barcode", folder: String = "folder") = CodeItem(
        id = id,
        text = text,
        format = "Code 128-B",
        createdAt = 100L,
        favorite = true,
        folder = folder,
        inHistory = true,
    )
}
