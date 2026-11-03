package com.luckyalanzhou.barcodegenerator.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RoomBarcodeRepositorySearchTest {
    @Test
    fun wildcardCharactersAreSearchedAsLiteralText() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            BarcodeDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val dao = database.barcodeDao()
            dao.upsertItems(
                listOf(
                    CodeItemEntity(1L, "contains % marker", "Code 128-B", 3L, true, "Root", false),
                    CodeItemEntity(2L, "contains _ marker", "Code 128-B", 2L, true, "Root", false),
                    CodeItemEntity(3L, "contains \\ marker", "Code 128-B", 1L, true, "Root", false),
                    CodeItemEntity(4L, "ordinary text", "Code 128-B", 0L, true, "Root", false),
                ),
            )
            dao.upsertGroups(
                listOf(
                    FavoriteGroupEntity(1L, "Root", "contains % marker", 3L),
                    FavoriteGroupEntity(2L, "Root", "contains _ marker", 2L),
                    FavoriteGroupEntity(3L, "Root", "contains \\ marker", 1L),
                    FavoriteGroupEntity(4L, "Root", "ordinary name", 0L),
                ),
            )
            dao.saveGroupItems(
                listOf(
                    FavoriteGroupItemEntity(1L, 1L, 0),
                    FavoriteGroupItemEntity(2L, 2L, 0),
                    FavoriteGroupItemEntity(3L, 3L, 0),
                    FavoriteGroupItemEntity(4L, 4L, 0),
                ),
            )
            val repository = RoomBarcodeRepository(database)

            assertEquals(listOf(1L), repository.searchFavoriteItems("%", 50, null).map { it.id })
            assertEquals(listOf(2L), repository.searchFavoriteItems("_", 50, null).map { it.id })
            assertEquals(listOf(3L), repository.searchFavoriteItems("\\", 50, null).map { it.id })
            assertEquals(listOf(1L), repository.searchFavoriteGroups("%", 50, null).map { it.id })
            assertEquals(listOf(2L), repository.searchFavoriteGroups("_", 50, null).map { it.id })
            assertEquals(listOf(3L), repository.searchFavoriteGroups("\\", 50, null).map { it.id })
        } finally {
            database.close()
        }
    }

}
