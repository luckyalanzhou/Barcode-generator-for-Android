package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.*
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import java.io.IOException

class FavoritesPartialLoadMutationTest {
    @Test
    fun favoriteSuccessWaitsForCommitAndFailureSurvivesMissingSubscriber() = runBlocking {
        val store = LibraryStateStore().apply {
            replace(listOf(CodeItem(20, "new", "QR_CODE", 2)), emptyList(), listOf("new"))
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = IOException("disk full")
        val repository = Proxy.newProxyInstance(BarcodeRepository::class.java.classLoader, arrayOf(BarcodeRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "applyFavoritesMutation" -> {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    throw failure
                }
                "loadItemsByIds" -> store.itemsSnapshot()
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        var mutationStarted = false
        val save = async { runCatching { FavoritesMutationCoordinator(
            store = store,
            persistence = persistence,
            onMutationStarted = { mutationStarted = true },
        )
            .saveResultAsFavorite(listOf(20), null, null, "new", "new file") } }
        try {
            withTimeout(5000) { while (entered.count > 0) delay(5) }
            assertTrue(mutationStarted)
            assertFalse(save.isCompleted)
            assertTrue("failed save must not publish an in-memory favorite group", store.groupsSnapshot().isEmpty())
            assertFalse("failed save must not mark the barcode as favorite", store.itemsSnapshot().single().favorite)
            assertNull(store.favoriteIdentity(1L))
        } finally { release.countDown() }
        assertNotNull(save.await().exceptionOrNull())
        assertTrue(store.groupsSnapshot().isEmpty())
        assertFalse(store.itemsSnapshot().single().favorite)
        assertNull(store.favoriteIdentity(1L))
        // 失败已发生之后才订阅，恢复通知仍必须送达。
        assertNotNull(withTimeout(5000) { persistence.writeFailures.first() })
    }

    @Test
    fun searchOnlyGroupRenamePersistsByIdAndUpdatesCanonicalIdentity() = runBlocking {
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
        assertTrue(FavoritesMutationCoordinator(store, persistence).renameGroupAndPersist(200, "renamed"))
        assertTrue(written.await(5, TimeUnit.SECONDS))
        assertEquals(200L, editedId)
        assertEquals("renamed", editedName)
        assertEquals("renamed", store.favoriteIdentity(200L)?.name)
        assertTrue(store.groupsSnapshot().isEmpty())
        assertEquals("original", search.groupsSnapshot().single().name)
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
        assertTrue(runBlocking { mutations.saveResultAsFavorite(listOf(20), null, null, "new", "new file") })
        assertTrue(written.await(5, TimeUnit.SECONDS))
        val saved = requireNotNull(snapshot)
        assertTrue(saved.items.first { it.id == 10L }.favorite)
        assertTrue(store.itemsSnapshot().first { it.id == 10L }.favorite)
        assertEquals(setOf(2L), saved.replaceGroupLinkIds)
    }

    @Test
    fun confirmedOverwriteRemovesEveryHiddenDuplicateIdentityButKeepsUnrelatedFavorites() = runBlocking {
        val store = LibraryStateStore().apply {
            replace(
                newItems = listOf(
                    CodeItem(10, "old target", "QR_CODE", 1, favorite = true, folder = "Root"),
                    CodeItem(20, "replacement", "QR_CODE", 2),
                ),
                newGroups = listOf(FavoriteGroup(1, "Root", "Duplicate", 1, mutableListOf(10))),
                newFolders = listOf("Root"),
                identityGroups = listOf(
                    FavoriteGroup(1, "Root", "Duplicate", 1, mutableListOf()),
                    FavoriteGroup(2, "Root", "Duplicate", 2, mutableListOf()),
                    FavoriteGroup(3, "Root", "Unrelated", 3, mutableListOf()),
                ),
            )
        }
        var snapshot: BarcodeSnapshot? = null
        val repository = Proxy.newProxyInstance(BarcodeRepository::class.java.classLoader, arrayOf(BarcodeRepository::class.java)) { _, method, args ->
            when (method.name) {
                "applyFavoritesMutation" -> { snapshot = args[0] as BarcodeSnapshot; Unit }
                "loadItemsByIds" -> store.itemsSnapshot()
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })

        val saved = FavoritesMutationCoordinator(store, persistence)
            .saveResultAsFavorite(listOf(20), editingGroupId = null, targetGroupId = 1, folder = "Root", name = "Duplicate")

        assertTrue("Confirming overwrite must replace all legacy duplicates", saved)
        assertEquals(listOf(20L), store.groupsSnapshot().single { it.id == 1L }.itemIds)
        assertNull(store.favoriteIdentity(2L))
        assertEquals("Unrelated", store.favoriteIdentity(3L)?.name)
        assertNotNull(snapshot)
        assertEquals(setOf(2L), snapshot?.deleteGroupIds)
    }

    @Test
    fun savingNewFavoriteRejectsExhaustedGroupIdSpaceBeforeMutation() = runBlocking {
        val existing = CodeItem(10, "existing", "QR_CODE", 1, true, "old")
        val generated = CodeItem(20, "new", "QR_CODE", 2)
        val maximumIdGroup = FavoriteGroup(Long.MAX_VALUE, "old", "max file", 1, mutableListOf(10))
        val store = LibraryStateStore().apply {
            replace(listOf(existing, generated), listOf(maximumIdGroup), listOf("old"))
        }
        assertEquals(Long.MAX_VALUE, store.maxFavoriteGroupId())
        var persistedSnapshot: BarcodeSnapshot? = null
        val repository = Proxy.newProxyInstance(BarcodeRepository::class.java.classLoader, arrayOf(BarcodeRepository::class.java)) { _, method, args ->
            check(method.name == "applyFavoritesMutation")
            persistedSnapshot = args[0] as BarcodeSnapshot
            Unit
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })

        val error = runCatching {
            FavoritesMutationCoordinator(store, persistence)
                .saveResultAsFavorite(listOf(20), null, null, "new", "new file")
        }.exceptionOrNull()

        assertTrue("Exhausted favorite group IDs must be rejected", error is IllegalArgumentException)
        assertNull(persistedSnapshot)
        assertEquals(listOf(Long.MAX_VALUE), store.groupsSnapshot().map { it.id })

        assertTrue(
            FavoritesMutationCoordinator(store, persistence)
                .saveResultAsFavorite(listOf(20), Long.MAX_VALUE, Long.MAX_VALUE, "new", "renamed file"),
        )
        assertEquals(Long.MAX_VALUE, persistedSnapshot?.groups?.single()?.id)
    }
}
