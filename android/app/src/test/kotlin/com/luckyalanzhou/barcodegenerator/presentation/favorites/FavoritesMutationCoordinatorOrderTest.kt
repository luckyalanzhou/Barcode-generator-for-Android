package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeData
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.composeFavoriteRows
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class FavoritesMutationCoordinatorOrderTest {
    private val items = listOf(
        CodeItem(10L, "ten", "Code 128-B", 1L),
        CodeItem(20L, "twenty", "Code 128-B", 2L),
        CodeItem(30L, "thirty", "Code 128-B", 3L),
    )

    @Test
    fun selectedFavoriteItemsFollowGeneratedResultOrder() {
        val selected = orderedFavoriteItems(items, listOf(30L, 10L, 20L))

        assertEquals(listOf(30L, 10L, 20L), selected?.map { it.id })
    }

    @Test
    fun missingResultItemRejectsPartialFavoriteInsteadOfSilentlyChangingItsContents() {
        assertNull(orderedFavoriteItems(items, listOf(30L, 999L)))
    }

    @Test
    fun renamingFavoriteMovesItsCurrentTreeRowToPersistedRecencyOrder() = runBlocking {
        val groups = listOf(
            FavoriteGroup(2L, "Root", "second", 20L, mutableListOf()),
            FavoriteGroup(1L, "Root", "first", 10L, mutableListOf()),
        )
        val store = LibraryStateStore().apply { replace(emptyList(), groups, listOf("Root")) }
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            check(method.name == "updateFavoriteGroupMetadata")
            Unit
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })

        assertTrue(FavoritesMutationCoordinator(store, persistence).renameGroupAndPersist(1L, "renamed"))
        persistence.awaitPendingWrites()

        val state = store.snapshot(isReady = true)
        val rows = composeFavoriteRows(
            FavoritesTreeData(state.items, state.groups, state.folders),
            query = "",
            collapsedFolders = emptySet(),
        )
        assertEquals(listOf("Root", "renamed", "second"), rows.map { it.label })
    }

    @Test
    fun groupRenameDoesNotReportSuccessBeforeDatabaseCommit() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val store = LibraryStateStore().apply {
            replace(
                emptyList(),
                listOf(FavoriteGroup(1L, "Root", "before", 1L, mutableListOf())),
                listOf("Root"),
            )
        }
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            check(method.name == "updateFavoriteGroupMetadata")
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Unit>
            started.complete(Unit)
            releaseWrite.invokeOnCompletion { failure ->
                continuation.resumeWith(
                    if (failure == null) Result.success(Unit) else Result.failure(failure),
                )
            }
            COROUTINE_SUSPENDED
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val mutations = FavoritesMutationCoordinator(store, persistence)
        val rename = async { mutations.renameGroupAndPersist(1L, "after") }

        try {
            started.await()
            assertFalse("rename must remain pending until Room confirms the write", rename.isCompleted)
            assertEquals("group name must wait for the Room receipt", "before", store.groupsSnapshot().single().name)
            assertEquals("favorite identity must wait for the Room receipt", "before", store.favoriteIdentity(1L)?.name)
            releaseWrite.complete(Unit)
            assertTrue(rename.await())
            assertEquals("after", store.groupsSnapshot().single().name)
            assertEquals("after", store.favoriteIdentity(1L)?.name)
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun groupMoveDoesNotPublishBeforeDatabaseCommit() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val store = LibraryStateStore().apply {
            replace(emptyList(), listOf(FavoriteGroup(1L, "Root", "file", 1L, mutableListOf())), listOf("Root"))
        }
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            check(method.name == "updateFavoriteGroupMetadata")
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Unit>
            started.complete(Unit)
            releaseWrite.invokeOnCompletion { failure ->
                continuation.resumeWith(
                    if (failure == null) Result.success(Unit) else Result.failure(failure),
                )
            }
            COROUTINE_SUSPENDED
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val mutations = FavoritesMutationCoordinator(store, persistence)
        val move = async { mutations.moveGroupAndPersist(1L, "Other") }

        try {
            started.await()
            assertFalse("group move must remain pending until Room confirms the write", move.isCompleted)
            assertEquals("Root", store.groupsSnapshot().single().folder)
            assertEquals("Root", store.favoriteIdentity(1L)?.folder)
            releaseWrite.complete(Unit)
            assertTrue(move.await())
            assertEquals("Other", store.groupsSnapshot().single().folder)
            assertEquals("Other", store.favoriteIdentity(1L)?.folder)
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun groupRenameFailureLeavesPersistedIdentityInMemory() = runBlocking {
        val expectedError = IllegalStateException("group write failed")
        val started = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val store = LibraryStateStore().apply {
            replace(emptyList(), listOf(FavoriteGroup(1L, "Root", "before", 1L, mutableListOf())), listOf("Root"))
        }
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            check(method.name == "updateFavoriteGroupMetadata")
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Unit>
            started.complete(Unit)
            releaseWrite.invokeOnCompletion { failure ->
                continuation.resumeWith(Result.failure(requireNotNull(failure)))
            }
            COROUTINE_SUSPENDED
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val mutations = FavoritesMutationCoordinator(store, persistence)
        val rename = async { runCatching { mutations.renameGroupAndPersist(1L, "after") } }

        try {
            started.await()
            releaseWrite.completeExceptionally(expectedError)
            assertSame(expectedError, rename.await().exceptionOrNull())
            assertEquals("before", store.groupsSnapshot().single().name)
            assertEquals("before", store.favoriteIdentity(1L)?.name)
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun folderRenameDoesNotReportSuccessBeforeDatabaseCommit() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val store = LibraryStateStore().apply {
            replace(emptyList(), emptyList(), listOf("Root", "Root/Child"))
        }
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            check(method.name == "renameFavoriteFolder")
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Unit>
            started.complete(Unit)
            releaseWrite.invokeOnCompletion { failure ->
                continuation.resumeWith(
                    if (failure == null) Result.success(Unit) else Result.failure(failure),
                )
            }
            COROUTINE_SUSPENDED
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val mutations = FavoritesMutationCoordinator(store, persistence)
        val rename = async { mutations.renameFolderAndPersist("Root", "Renamed") }

        try {
            started.await()
            assertFalse("folder rename must remain pending until Room confirms the write", rename.isCompleted)
            assertEquals("in-memory folders must wait for the Room receipt", listOf("Root", "Root/Child"), store.foldersSnapshot())
            releaseWrite.complete(Unit)
            assertTrue(rename.await())
            assertEquals(listOf("Renamed", "Renamed/Child"), store.foldersSnapshot())
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }

    @Test
    fun folderRenamePropagatesDatabaseFailure() = runBlocking {
        val expectedError = IllegalStateException("folder write failed")
        val started = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val store = LibraryStateStore().apply {
            replace(emptyList(), emptyList(), listOf("Root"))
        }
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            check(method.name == "renameFavoriteFolder")
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Unit>
            started.complete(Unit)
            releaseWrite.invokeOnCompletion { failure ->
                continuation.resumeWith(Result.failure(requireNotNull(failure)))
            }
            COROUTINE_SUSPENDED
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val mutations = FavoritesMutationCoordinator(store, persistence)
        val rename = async { runCatching { mutations.renameFolderAndPersist("Root", "Renamed") } }

        try {
            started.await()
            releaseWrite.completeExceptionally(expectedError)
            assertSame(expectedError, rename.await().exceptionOrNull())
            assertEquals("failed persistence must not publish the renamed path", listOf("Root"), store.foldersSnapshot())
        } finally {
            releaseWrite.complete(Unit)
            persistence.awaitPendingWrites()
        }
    }
}
