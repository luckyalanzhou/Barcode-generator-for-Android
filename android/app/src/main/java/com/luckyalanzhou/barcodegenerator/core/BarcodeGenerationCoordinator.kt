package com.luckyalanzhou.barcodegenerator

import androidx.lifecycle.ViewModel
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.ui.AppRoute

/** 生成流程协调器：隔离输入解析、结果快照和持久化，避免 ViewModel 继续膨胀。 */
class BarcodeGenerationCoordinator(
    private val useCase: GenerateBarcodesUseCase,
    private val items: MutableList<CodeItem>,
    private val readDraft: () -> List<String>,
    private val readResult: () -> ResultUiState,
    private val updateResult: (ResultUiState) -> Unit,
    private val persistItems: () -> Unit,
    private val navigate: (AppRoute) -> Unit,
) {
    fun generate(formatName: String): GenerateBarcodesUseCase.Output {
        val result = useCase.execute(readDraft(), formatName, items)
        if (!result.isValid) return result

        val currentResult = readResult()
        val editingFavorite = currentResult.selectedFavoriteGroup
            ?.takeIf { currentResult.returnPage == AppRoute.Favorites }
        val generated = result.items
        items.addAll(0, generated)
        persistItems()
        updateResult(
            currentResult.copy(
                items = generated,
                selectedFavoriteGroup = editingFavorite,
                showingHistoryResult = false,
                returnPage = if (editingFavorite != null) AppRoute.Favorites else AppRoute.Generate,
            ),
        )
        navigate(AppRoute.Results)
        return result
    }
}
