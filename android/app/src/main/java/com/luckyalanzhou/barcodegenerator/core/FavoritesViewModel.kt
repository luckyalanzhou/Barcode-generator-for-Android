package com.luckyalanzhou.barcodegenerator

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FavoritesUiState(
    val groups: List<FavoriteGroup> = emptyList(),
    val folders: List<String> = emptyList(),
    val items: List<CodeItem> = emptyList(),
)

/** 收藏页只读展示状态；写入仍由现有持久化队列保证顺序。 */
class FavoritesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    fun sync(groups: List<FavoriteGroup>, folders: List<String>, items: List<CodeItem>) {
        _uiState.value = FavoritesUiState(groups, folders, items)
    }
}
