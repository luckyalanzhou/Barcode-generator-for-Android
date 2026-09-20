package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesStateStoreTest {
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
