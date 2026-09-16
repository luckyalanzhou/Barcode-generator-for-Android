package com.luckyalanzhou.barcodegenerator

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FavoritesUiState(
    val groups: List<FavoriteGroup> = emptyList(),
    val folders: List<String> = emptyList(),
    val items: List<CodeItem> = emptyList(),
)

/** 收藏页只读展示状态；写入仍由现有持久化队列保证顺序。 */
@HiltViewModel
class FavoritesViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    fun sync(groups: List<FavoriteGroup>, folders: List<String>, items: List<CodeItem>) {
        // 业务层对象是可变的；展示层必须保存快照，否则 StateFlow 可能把重命名/删除前后
        // 的同一对象引用判定为相等，Compose 就不会刷新。
        _uiState.value = FavoritesUiState(
            groups = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) },
            folders = folders.toList(),
            items = items.map { it.copy() },
        )
    }
}
