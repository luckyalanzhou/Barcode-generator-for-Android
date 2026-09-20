package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class FavoritesStateStoreTest {
    @Test
    fun snapshotIsImmutableFromStoreMutations() {
        val store = FavoritesStateStore()
        val item = CodeItem(1L, "A", "Code 128-B")
        val group = FavoriteGroup(1L, "一级", "收藏", 1L, mutableListOf(1L))
        store.replace(listOf(item), listOf(group), listOf("一级"))

        val snapshot = store.snapshot(isReady = true)
        store.edit {
            items[0].text = "B"
            groups[0].itemIds.add(2L)
            folders.add("二级")
        }

        assertEquals("A", snapshot.items.single().text)
        assertEquals(listOf(1L), snapshot.groups.single().itemIds)
        assertEquals(listOf("一级"), snapshot.folders)
        assertNotSame(store.itemsSnapshot(), snapshot.items)
    }
}
