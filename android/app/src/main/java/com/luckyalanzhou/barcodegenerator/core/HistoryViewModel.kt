package com.luckyalanzhou.barcodegenerator

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HistoryUiState(
    val entries: List<Pair<Long, List<CodeItem>>> = emptyList(),
)

/** 历史页批次状态；页面不再负责从全量 Activity 列表分组。 */
class HistoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun sync(items: List<CodeItem>) {
        _uiState.value = HistoryUiState(
            items.filter { it.inHistory }.map { it.copy() }.groupBy { it.createdAt }.toList().sortedByDescending { it.first },
        )
    }
}
