package com.luckyalanzhou.barcodegenerator

import androidx.lifecycle.ViewModel
import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.NavigationRoute as AppRoute

/** 生成流程协调器：隔离输入解析、结果快照和持久化，避免 ViewModel 继续膨胀。 */
internal class GenerateCoordinator(
    private val useCase: GenerateBarcodesUseCase,
    private val store: FavoritesStateStore,
    private val readDraft: () -> List<String>,
    private val persistItems: () -> Unit,
) {
    data class Result(
        val output: GenerateBarcodesUseCase.Output,
        val uiState: ResultUiState?,
    )

    fun generate(formatName: String, currentResult: ResultUiState): Result {
        val result = useCase.execute(readDraft(), formatName, store.itemsSnapshot())
        if (!result.isValid) return Result(result, null)

        val editingFavorite = currentResult.selectedFavoriteGroup
            ?.takeIf { currentResult.returnPage == AppRoute.Favorites }
        val generated = result.items
        store.edit { items.addAll(0, generated) }
        persistItems()
        val nextResult = currentResult.copy(
            items = generated,
            selectedFavoriteGroup = editingFavorite,
            showingHistoryResult = false,
            returnPage = if (editingFavorite != null) AppRoute.Favorites else AppRoute.Generate,
        )
        return Result(result, nextResult)
    }
}
