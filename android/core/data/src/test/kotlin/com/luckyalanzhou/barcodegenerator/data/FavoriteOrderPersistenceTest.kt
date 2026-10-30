package com.luckyalanzhou.barcodegenerator.data

import androidx.room.Room
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 收藏条码顺序与正文重复项在数据库关闭、重开后仍保持稳定。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoriteOrderPersistenceTest {
    @Test
    fun orderedLinksAndRepeatedTextSurviveDatabaseReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        var database = openDatabase()
        val orderedIds = listOf(71L, 4L, 39L)
        val itemTexts = listOf("SAME-CODE", "MIDDLE-CODE", "SAME-CODE")
        val groupId = 12L

        try {
            RoomBarcodeRepository(database).appendSnapshot(
                BarcodeSnapshot(
                    items = orderedIds.zip(itemTexts).mapIndexed { index, (id, text) ->
                        CodeItem(
                            id = id,
                            text = text,
                            format = "Code 128-B",
                            createdAt = index.toLong(),
                            favorite = true,
                            folder = "Saved",
                            inHistory = true,
                        )
                    },
                    groups = listOf(FavoriteGroup(groupId, "", "ordered", 1L, orderedIds.toMutableList())),
                    links = orderedIds.mapIndexed { position, itemId ->
                        FavoriteGroupItem(groupId, itemId, position)
                    },
                    folders = emptyList(),
                ),
            )
            database.close()

            database = openDatabase()
            val repository = RoomBarcodeRepository(database)

            assertEquals(orderedIds, repository.loadGroupItemIds(groupId))
            assertEquals(orderedIds, repository.loadFavoriteGroupsByIds(listOf(groupId)).single().itemIds)
            assertEquals(itemTexts, repository.loadItemsByIds(orderedIds).map(CodeItem::text))
        } finally {
            database.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private fun openDatabase(): BarcodeDatabase =
        Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            BarcodeDatabase::class.java,
            DATABASE_NAME,
        ).allowMainThreadQueries().build()

    private companion object {
        const val DATABASE_NAME = "favorite-order-persistence-test.db"
    }
}
