package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedSnapshotIdsTest {
    @Test
    fun staleImportIdsAreRemappedTogetherWithoutChangingBarcodeOrder() {
        val draft = BarcodeSnapshot(
            listOf(CodeItem(1, "second", "QR_CODE"), CodeItem(2, "first", "QR_CODE")),
            listOf(FavoriteGroup(1, "", "file", 2, mutableListOf(2, 1))),
            listOf(FavoriteGroupItem(1, 2, 0), FavoriteGroupItem(1, 1, 1)), emptyList(),
        )
        val committed = allocateImportedSnapshotIds(draft, 900, 80)
        assertEquals(listOf(901L, 902L), committed.items.map { it.id })
        assertEquals(81L, committed.groups.single().id)
        assertEquals(listOf(902L, 901L), committed.groups.single().itemIds)
        assertEquals(listOf(FavoriteGroupItem(81, 902, 0), FavoriteGroupItem(81, 901, 1)), committed.links)
        assertEquals(listOf(2L, 1L), draft.groups.single().itemIds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun exhaustedIdSpaceRejectsImport() {
        allocateImportedSnapshotIds(BarcodeSnapshot(listOf(CodeItem(1, "keep", "QR_CODE")), emptyList(), emptyList(), emptyList()), Long.MAX_VALUE, 0)
    }
}
