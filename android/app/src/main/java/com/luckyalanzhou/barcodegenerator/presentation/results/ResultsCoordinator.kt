package com.luckyalanzhou.barcodegenerator.presentation.results

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute as AppRoute
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the result-page snapshot and the transitions that prepare it.
 * Navigation itself remains outside this coordinator; callers decide where to go.
 */
internal class ResultsCoordinator(initial: ResultUiState = ResultUiState()) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    fun current(): ResultUiState = _state.value

    fun prepareGenerateTab() {
        _state.update {
            it.copy(
                selectedFavoriteGroup = null,
                returnPage = AppRoute.Generate,
                showingHistoryResult = false,
            )
        }
    }

    fun clearSelectedFavoriteGroup() {
        _state.update { it.copy(selectedFavoriteGroup = null) }
    }

    fun showFavoriteResult(group: FavoriteGroup, items: List<CodeItem>) {
        _state.update {
            it.copy(
                selectedFavoriteGroup = group,
                items = items,
                showingHistoryResult = false,
                returnPage = AppRoute.Favorites,
                isRestoring = false,
                restoreFailed = false,
            )
        }
    }

    fun showHistoryResult(items: List<CodeItem>) {
        _state.update {
            it.copy(
                items = items.sortedBy(CodeItem::id),
                showingHistoryResult = true,
                returnPage = AppRoute.History,
                isRestoring = false,
                restoreFailed = false,
            )
        }
    }

    fun updateGeneratedResult(state: ResultUiState) {
        _state.value = state
    }

}
