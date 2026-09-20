package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.InterchangeFavorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTransferManagerTest {
    @Test
    fun importEntitiesPreserveFolderAndTexts() {
        val backup = InterchangeBackup(
            favorites = listOf(InterchangeFavorite(null, "文件", "一级", "二级", "code128", 11L, listOf("  A B  "))),
            folders = listOf("一级", "一级/二级"),
        )

        val entities = FavoritesTransferManager.appendEntities(backup, emptyList(), emptyList(), emptyList())

        assertEquals("一级/二级", entities.groups.single().folder)
        assertEquals("文件", entities.groups.single().name)
        assertEquals("  A B  ", entities.items.single().text)
        assertTrue(entities.folders.any { it.name == "一级/二级" })
    }

    @Test
    fun importKeepsSameContentFavoritesWithDifferentIds() {
        val backup = InterchangeBackup(
            favorites = listOf(
                InterchangeFavorite("101", "重复名称", "一级", "", "code128", 11L, listOf("ABC")),
                InterchangeFavorite("102", "重复名称", "一级", "", "code128", 12L, listOf("ABC")),
            ),
            folders = listOf("一级"),
        )

        val entities = FavoritesTransferManager.appendEntities(backup, emptyList(), emptyList(), emptyList())

        assertEquals(2, entities.groups.size)
        assertEquals(2, entities.items.size)
        assertEquals(2, entities.links.size)
    }

    @Test
    fun importDoesNotCollapseSameContentWhenBarcodeTypeDiffers() {
        val backup = InterchangeBackup(
            favorites = listOf(
                InterchangeFavorite("201", "同名", "", "", "code128", 11L, listOf("123")),
                InterchangeFavorite("202", "同名", "", "", "qr", 12L, listOf("123")),
            ),
            folders = emptyList(),
        )

        val entities = FavoritesTransferManager.appendEntities(backup, emptyList(), emptyList(), emptyList())

        assertEquals(2, entities.groups.size)
        assertEquals(listOf("Code 128-B", "QR Code"), entities.items.map { it.format })
    }

    @Test
    fun restoreRejectsNonZipInput() {
        try {
            FavoritesTransferManager.restore("not-a-zip".toByteArray())
            error("expected invalid backup to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("ZIP"))
        }
    }

    @Test
    fun favoritePathSupportsWrappedCrossPlatformZipRoot() {
        assertEquals("一级/文件.json", FavoritesTransferManager.favoriteRelativePath("backup-root/favorites/一级/文件.json"))
        assertEquals("一级/文件.json", FavoritesTransferManager.favoriteRelativePath("favorites/一级/文件.json"))
        assertEquals(null, FavoritesTransferManager.favoriteRelativePath("other/一级/文件.json"))
    }
}
