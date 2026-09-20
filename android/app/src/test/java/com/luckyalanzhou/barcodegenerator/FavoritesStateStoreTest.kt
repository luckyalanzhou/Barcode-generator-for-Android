package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesStateStoreTest {
    @Test
    fun unloadedGroupsAreNotMarkedAsAuthoritativeLinkSnapshots() {
        val store = FavoritesStateStore()
        val group = FavoriteGroup(7L, "一级", "文件", 7L, mutableListOf())

        store.replace(emptyList(), listOf(group), listOf("一级"))
        assertEquals(emptySet<Long>(), store.loadedGroupLinkIdsSnapshot())

        store.markGroupLinksLoaded(group.id)
        assertEquals(setOf(group.id), store.loadedGroupLinkIdsSnapshot())
    }

    @Test
    fun concurrentSnapshotsAndEditsRemainConsistent() = runBlocking {
        val store = FavoritesStateStore()
        val writers = (1L..8L).map { writer ->
            async(Dispatchers.Default) {
                repeat(100) { index ->
                    store.edit { items += CodeItem(writer * 1_000 + index, "内容", "Code 128-B") }
                    store.snapshot(isReady = true)
                }
            }
        }

        writers.awaitAll()

        assertEquals(800, store.itemsSnapshot().size)
        assertEquals(800, store.snapshot(true).items.size)
    }
}
