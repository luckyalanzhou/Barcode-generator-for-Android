package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.FavoritesTransferManager
import com.luckyalanzhou.barcodegenerator.data.CodeItemEntity
import com.luckyalanzhou.barcodegenerator.data.FavoriteGroupEntity
import com.luckyalanzhou.barcodegenerator.data.FavoriteGroupItemEntity
import com.luckyalanzhou.barcodegenerator.domain.InterchangeFavorite
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTransferManagerTest {
    @Test
    fun favoriteFileUsesPrimaryAndSecondaryFolderPath() {
        val favorite = InterchangeFavorite("7", "收藏", "一级", "二级", "code128", 1L, listOf("123"))

        assertEquals("favorites/一级/二级/7.json", FavoritesTransferManager.favoriteZipPath(favorite))
    }

    @Test
    fun emptyFavoriteGroupIsPreservedDuringImport() {
        val backup = InterchangeBackup(
            favorites = listOf(InterchangeFavorite(null, "95.7G203GC0E", "一级", "二级", "code128", 1L, emptyList())),
            folders = listOf("一级", "一级/二级")
        )
        val entities = FavoritesTransferManager.appendEntities(backup, emptyList(), emptyList(), emptyList())

        assertEquals(1, entities.groups.size)
        assertEquals("95.7G203GC0E", entities.groups.single().name)
        assertTrue(entities.items.isEmpty())
    }

    @Test
    fun barcodeTextKeepsLeadingAndTrailingSpaces() {
        val backup = InterchangeBackup(
            favorites = listOf(InterchangeFavorite(null, "收藏", "", "", "code128", 1L, listOf(" A B "))),
            folders = emptyList(),
        )
        val entities = FavoritesTransferManager.appendEntities(backup, emptyList(), emptyList(), emptyList())

        assertEquals(" A B ", entities.items.single().text)
    }

    @Test
    fun importAppendsNewFavoritesWithoutReplacingExistingEntities() {
        val backup = InterchangeBackup(
            favorites = listOf(InterchangeFavorite(null, "新收藏", "新文件夹", "", "code128", 2L, listOf("456"))),
            folders = listOf("新文件夹"),
        )
        val existingItems = listOf(CodeItemEntity(10L, "123", "Code 128-B", 1L, true, "旧文件夹", false))
        val existingGroups = listOf(FavoriteGroupEntity(20L, "旧文件夹", "旧收藏", 1L))
        val existingLinks = listOf(FavoriteGroupItemEntity(20L, 10L))

        val entities = FavoritesTransferManager.appendEntities(backup, existingItems, existingGroups, existingLinks)

        assertEquals(1, entities.items.size)
        assertEquals(11L, entities.items.single().id)
        assertEquals(1, entities.groups.size)
        assertEquals(21L, entities.groups.single().id)
        assertEquals(FavoriteGroupItemEntity(21L, 11L), entities.links.single())
        assertEquals(listOf("新文件夹"), entities.folders.map { it.name })
    }
}
