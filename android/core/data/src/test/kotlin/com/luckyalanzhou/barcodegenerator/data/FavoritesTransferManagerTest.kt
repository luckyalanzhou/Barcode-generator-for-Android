package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.InterchangeFavorite
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
    fun importKeepsSameBarcodeDataWhenFavoriteNamesDiffer() {
        val backup = InterchangeBackup(
            favorites = listOf(
                InterchangeFavorite("301", "文件A", "一级", "", "code128", 11L, listOf("SAME-CODE")),
                InterchangeFavorite("302", "文件B", "一级", "", "code128", 12L, listOf("SAME-CODE")),
            ),
            folders = listOf("一级"),
        )

        val entities = FavoritesTransferManager.appendEntities(backup, emptyList(), emptyList(), emptyList())

        assertEquals(listOf("文件A", "文件B"), entities.groups.map { it.name })
        assertEquals(2, entities.items.size)
        assertEquals(listOf("SAME-CODE", "SAME-CODE"), entities.items.map { it.text })
    }

    @Test
    fun importIdentityDoesNotDependOnBarcodeContent() {
        val existingGroups = listOf(FavoriteGroupEntity(10L, "一级", "文件", 1L))
        val existingItems = listOf(CodeItemEntity(20L, "OLD", "Code 128-B", 1L, true, "一级", false))
        val existingLinks = listOf(FavoriteGroupItemEntity(10L, 20L))
        val backup = InterchangeBackup(
            favorites = listOf(InterchangeFavorite("30", "文件", "一级", "", "code128", 2L, listOf("NEW"))),
            folders = listOf("一级"),
        )

        val entities = FavoritesTransferManager.appendEntities(backup, existingItems, existingGroups, existingLinks)

        assertTrue(entities.groups.isEmpty())
        assertTrue(entities.items.isEmpty())
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
    fun exportWritesReadableZipWithoutClosingCallerOutput() {
        val output = CloseAwareOutputStream()
        val snapshot = BarcodeSnapshot(
            items = listOf(
                CodeItem(5L, "  A B  ", "Code 128-B", createdAt = 12L),
                CodeItem(6L, "q\"\\\n\u0001", "Code 128-B", createdAt = 13L),
            ),
            groups = listOf(FavoriteGroup(7L, "一级/二级", "收藏", 11L, mutableListOf())),
            links = listOf(FavoriteGroupItem(7L, 5L), FavoriteGroupItem(7L, 6L)),
            folders = listOf("一级", "一级/二级"),
        )

        FavoritesTransferManager.export(output, snapshot)
        val zip = ZipInputStream(ByteArrayInputStream(output.toByteArray()))
        var entry = zip.nextEntry
        var json: String? = null
        while (entry != null) {
            if (entry.name.endsWith(".json")) json = zip.readBytes().decodeToString()
            zip.closeEntry()
            entry = zip.nextEntry
        }

        assertTrue(json.orEmpty().contains("\"folder\":\"一级/二级\""))
        assertTrue(json.orEmpty().contains("\"texts\":[\"  A B  \","))
        assertTrue(json.orEmpty().contains("\"q\\\"\\\\\\n\\u0001\""))
        assertEquals(false, output.closed)
    }

    @Test
    fun oversizedFavoriteIsRejectedBeforeWritingAnyBytes() {
        val output = ByteArrayOutputStream()
        val snapshot = BarcodeSnapshot(
            items = listOf(CodeItem(5L, "x".repeat(1024 * 1024), "Code 128-B")),
            groups = listOf(FavoriteGroup(7L, "", "收藏", 11L, mutableListOf())),
            links = listOf(FavoriteGroupItem(7L, 5L)),
            folders = emptyList(),
        )

        try {
            FavoritesTransferManager.export(output, snapshot)
            error("expected oversized favorite to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("1 MB"))
            assertEquals(0, output.size())
        }
    }

    @Test
    fun excessiveEntryCountIsRejectedBeforeWritingAnyBytes() {
        val output = ByteArrayOutputStream()
        val ids = 1L..2_048L
        val snapshot = BarcodeSnapshot(
            items = ids.map { CodeItem(it, "value-$it", "Code 128-B") },
            groups = ids.map { FavoriteGroup(it, "", "group-$it", it, mutableListOf()) },
            links = ids.map { FavoriteGroupItem(it, it) },
            folders = emptyList(),
        )

        try {
            FavoritesTransferManager.export(output, snapshot)
            error("expected excessive ZIP entry count to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("2048"))
            assertEquals(0, output.size())
        }
    }

    @Test
    fun restoreRejectsExpandedSizeOverLimitAcrossIgnoredFilesAndDirectories() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeRepeatedEntry(zip, "unrelated/first.bin", 16 * 1024 * 1024)
            writeRepeatedEntry(zip, "unrelated/second/", 16 * 1024 * 1024 + 1)
        }

        try {
            FavoritesTransferManager.restore(output.toByteArray())
            error("expected oversized ignored ZIP entries to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("32 MB"))
        }
    }


    @Test
    fun favoritePathSupportsWrappedCrossPlatformZipRoot() {
        assertEquals("一级/文件.json", FavoritesTransferManager.favoriteRelativePath("backup-root/favorites/一级/文件.json"))
        assertEquals("一级/文件.json", FavoritesTransferManager.favoriteRelativePath("favorites/一级/文件.json"))
        assertEquals(null, FavoritesTransferManager.favoriteRelativePath("other/一级/文件.json"))
    }

    private fun writeRepeatedEntry(zip: ZipOutputStream, name: String, size: Int) {
        zip.putNextEntry(ZipEntry(name))
        val block = ByteArray(16 * 1024) { 'x'.code.toByte() }
        var remaining = size
        while (remaining > 0) {
            val count = minOf(block.size, remaining)
            zip.write(block, 0, count)
            remaining -= count
        }
        zip.closeEntry()
    }

    private class CloseAwareOutputStream : ByteArrayOutputStream() {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
