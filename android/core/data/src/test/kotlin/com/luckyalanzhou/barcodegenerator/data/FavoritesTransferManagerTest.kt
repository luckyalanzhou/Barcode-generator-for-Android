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
    fun restoreRejectsNonZipInput() {
        try {
            FavoritesTransferManager.restore("not-a-zip".toByteArray())
            error("expected invalid backup to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("ZIP"))
        }
    }
}
