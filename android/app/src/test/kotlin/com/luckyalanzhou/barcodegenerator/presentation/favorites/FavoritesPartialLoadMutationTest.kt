package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.*
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FavoritesPartialLoadMutationTest {
    @Test
    fun searchOnlyGroupRenamePersistsByIdAndUpdatesSearchCache() {
        val group = FavoriteGroup(200, "old", "original", 1, mutableListOf(10))
        val store = LibraryStateStore().apply { replace(emptyList(), emptyList(), listOf("old"), listOf(group)) }
        val search = LibraryStateStore().apply { replace(emptyList(), listOf(group), listOf("old")) }
        val written = CountDownLatch(1)
        var editedId = 0L
        var editedName = ""
        val repository = Proxy.newProxyInstance(BarcodeRepository::class.java.classLoader, arrayOf(BarcodeRepository::class.java)) { _, method, args ->
            check(method.name == "updateFavoriteGroupMetadata")
            editedId = args[0] as Long
            editedName = args[1] as String
            written.countDown()
            Unit
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        assertTrue(FavoritesMutationCoordinator(store, persistence, search).renameGroupAndPersist(200, "renamed"))
        assertTrue(written.await(5, TimeUnit.SECONDS))
        assertEquals(200L, editedId)
        assertEquals("renamed", editedName)
        assertEquals("renamed", search.groupsSnapshot().single().name)
        assertTrue(store.groupsSnapshot().isEmpty())
    }
    @Test
    fun savingNewFavoriteDoesNotClearFavoriteWhoseLinksAreNotLoaded() {
        val existing = CodeItem(10, "existing", "QR_CODE", 1, true, "old")
        val generated = CodeItem(20, "new", "QR_CODE", 2)
        val store = LibraryStateStore().apply {
            replace(listOf(existing, generated), listOf(FavoriteGroup(1, "old", "old file", 1, mutableListOf())), listOf("old"))
        }
        val written = CountDownLatch(1)
        var snapshot: BarcodeSnapshot? = null
        val repository = Proxy.newProxyInstance(BarcodeRepository::class.java.classLoader, arrayOf(BarcodeRepository::class.java)) { _, method, args ->
            when (method.name) {
                "applyFavoritesMutation" -> { snapshot = args[0] as BarcodeSnapshot; written.countDown(); Unit }
                "loadItemsByIds" -> store.itemsSnapshot()
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })

        val mutations = FavoritesMutationCoordinator(store, persistence)
        assertTrue(mutations.saveResultAsFavorite(listOf(20), null, null, "new", "new file"))
        assertTrue(written.await(5, TimeUnit.SECONDS))
        val saved = requireNotNull(snapshot)
        assertTrue(saved.items.first { it.id == 10L }.favorite)
        assertTrue(store.itemsSnapshot().first { it.id == 10L }.favorite)
        assertEquals(setOf(2L), saved.replaceGroupLinkIds)
    }
}
