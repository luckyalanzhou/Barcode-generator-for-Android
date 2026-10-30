package com.luckyalanzhou.barcodegenerator.presentation.results

import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState

import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute as AppRoute
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 持有结果页快照，并协调进入结果页前所需的状态转换。
 * 导航本身由此协调器之外的调用方负责决定。
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
                selectedFavoriteGroup = null,
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
