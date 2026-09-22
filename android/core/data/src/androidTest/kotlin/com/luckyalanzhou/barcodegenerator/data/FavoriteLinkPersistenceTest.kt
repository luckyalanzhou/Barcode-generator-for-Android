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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteLinkPersistenceTest {
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

    private fun favoriteItem(text: String) = CodeItem(
        id = 1L,
        text = text,
        format = "Code 128-B",
        createdAt = 100L,
        favorite = true,
        folder = "folder",
        inHistory = true,
    )
}
