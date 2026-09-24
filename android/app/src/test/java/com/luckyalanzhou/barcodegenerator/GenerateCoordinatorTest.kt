package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesStateStore
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GenerateCoordinatorTest {
    @Test
    fun commitPersistsGeneratedItemsAndPreservesFavoriteReturnContext() {
        val existing = CodeItem(1L, "existing", "Code 128-B", 1L)
        val generated = CodeItem(2L, "new", "Code 128-B", 2L)
        val selectedGroup = FavoriteGroup(9L, "folder", "group", 9L, mutableListOf())
        val store = FavoritesStateStore().apply { edit { items += existing } }
        var persistCount = 0
        val coordinator = GenerateCoordinator(store) { persistCount++ }

        val result = coordinator.commit(
            listOf(generated),
            ResultUiState(
                showingHistoryResult = true,
                returnPage = NavigationRoute.Favorites,
                selectedFavoriteGroup = selectedGroup,
            ),
        )

        assertEquals(listOf(2L, 1L), store.itemsSnapshot().map(CodeItem::id))
        assertEquals(1, persistCount)
        assertFalse(result.showingHistoryResult)
        assertEquals(NavigationRoute.Favorites, result.returnPage)
        assertEquals(selectedGroup, result.selectedFavoriteGroup)
        assertEquals(listOf(generated), result.items)
    }
}
