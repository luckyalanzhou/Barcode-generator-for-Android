package com.luckyalanzhou.barcodegenerator.presentation.results

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute
import com.luckyalanzhou.barcodegenerator.ui.feature.results.hasSavedFavoriteFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteFileStatusTest {
    @Test
    fun savedFavoriteFileIsSelectedRegardlessOfBarcodeFavoriteFlags() {
        val state = ResultUiState(
            items = listOf(CodeItem(1, "content", "QR_CODE", favorite = false)),
            returnPage = NavigationRoute.Favorites,
            selectedFavoriteGroup = FavoriteGroup(7, "一级", "文件", 1, mutableListOf(1)),
        )

        assertTrue(state.hasSavedFavoriteFile())
    }

    @Test
    fun unsavedResultIsNotSelectedEvenWhenItsBarcodeItemsAreFavorited() {
        val state = ResultUiState(
            items = listOf(CodeItem(1, "content", "QR_CODE", favorite = true)),
            returnPage = NavigationRoute.Generate,
        )

        assertFalse(state.hasSavedFavoriteFile())
    }

    @Test
    fun showingHistoryClearsSelectedFavoriteFile() {
        val selectedGroup = FavoriteGroup(7, "一级", "文件", 1, mutableListOf(1))
        val coordinator = ResultsCoordinator(
            ResultUiState(
                returnPage = NavigationRoute.Favorites,
                selectedFavoriteGroup = selectedGroup,
            ),
        )

        coordinator.showHistoryResult(listOf(CodeItem(2, "history", "QR_CODE")))

        assertNull(coordinator.current().selectedFavoriteGroup)
        assertFalse(coordinator.current().hasSavedFavoriteFile())
    }
}
