package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesStateStore

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.presentation.*

import kotlinx.coroutines.flow.MutableStateFlow

/** 将可变领域集合转换为 Compose 可安全观察的不可变数据快照。 */
internal class BarcodeDataStateCoordinator(
    private val store: FavoritesStateStore,
    private val state: MutableStateFlow<BarcodeDataState>,
) {
    fun publish(isReady: Boolean = state.value.isReady) {
        state.value = store.snapshot(isReady)
    }
}
