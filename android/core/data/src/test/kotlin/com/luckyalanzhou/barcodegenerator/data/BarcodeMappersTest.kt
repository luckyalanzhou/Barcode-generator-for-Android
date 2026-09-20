package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeMappersTest {
    @Test
    fun snapshotTransferRestoresGroupItemLinks() {
        val snapshot = BarcodeSnapshot(
            items = listOf(CodeItem(7L, "ABC", "Code 128-B", 10L, true, "一级", false)),
            groups = listOf(FavoriteGroup(3L, "一级", "文件", 11L, mutableListOf(7L))),
            links = listOf(FavoriteGroupItem(3L, 7L)),
            folders = listOf("一级"),
        )

        val restored = snapshot.toTransferEntities().toSnapshot()

        assertEquals(snapshot.items, restored.items)
        assertEquals(snapshot.links, restored.links)
        assertEquals(listOf(7L), restored.groups.single().itemIds)
        assertEquals(snapshot.folders, restored.folders)
    }

    @Test
    fun entityRoundTripPreservesAllCodeItemFields() {
        val item = CodeItem(9L, "  A B  ", "QR_CODE", 42L, true, "一级/二级", true)

        assertEquals(item, item.toEntity().toDomain())
    }
}
