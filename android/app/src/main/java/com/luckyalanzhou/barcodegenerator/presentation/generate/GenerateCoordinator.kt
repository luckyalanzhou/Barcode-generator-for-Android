package com.luckyalanzhou.barcodegenerator.presentation.generate

import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute as AppRoute

/** Commits generated items to the shared barcode session and prepares the result snapshot. */
internal class GenerateCoordinator(
    private val store: LibraryStateStore,
    private val persistItems: () -> Unit,
) {
    fun commit(generated: List<CodeItem>, currentResult: ResultUiState): ResultUiState {
        val editingFavorite = currentResult.selectedFavoriteGroup
            ?.takeIf { currentResult.returnPage == AppRoute.Favorites }
        store.edit { items.addAll(0, generated) }
        persistItems()
        val nextResult = currentResult.copy(
            items = generated,
            selectedFavoriteGroup = editingFavorite,
            showingHistoryResult = false,
            returnPage = if (editingFavorite != null) AppRoute.Favorites else AppRoute.Generate,
        )
        return nextResult
    }
}
