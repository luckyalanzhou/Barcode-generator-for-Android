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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
