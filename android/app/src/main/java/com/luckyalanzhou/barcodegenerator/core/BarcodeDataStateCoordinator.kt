package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import kotlinx.coroutines.flow.MutableStateFlow

/** 将可变领域集合转换为 Compose 可安全观察的不可变数据快照。 */
internal class BarcodeDataStateCoordinator(
    private val items: List<CodeItem>,
    private val groups: List<FavoriteGroup>,
    private val folders: List<String>,
    private val state: MutableStateFlow<BarcodeDataState>,
) {
    fun publish(isReady: Boolean = state.value.isReady) {
        state.value = BarcodeDataState(
            items = items.map { it.copy() },
            groups = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) },
            folders = folders.toList(),
            isReady = isReady,
        )
    }
}
