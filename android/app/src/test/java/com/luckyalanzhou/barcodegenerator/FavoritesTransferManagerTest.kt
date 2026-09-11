package com.luckyalanzhou.barcodegenerator

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
}
