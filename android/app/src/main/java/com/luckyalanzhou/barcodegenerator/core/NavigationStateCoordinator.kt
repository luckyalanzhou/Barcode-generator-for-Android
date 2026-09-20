package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.NavigationRoute as AppRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Owns page selection mutations while keeping the ViewModel's public API stable. */
internal class NavigationStateCoordinator(
    private val state: MutableStateFlow<AppUiState>,
) {
    fun navigateTo(route: AppRoute) {
        state.update {
            it.copy(
                page = route,
                selectedTab = when (route.mainTabIndex) {
                    0, 1, 2, 3 -> route.mainTabIndex
                    else -> it.selectedTab
                },
            )
        }
    }

    fun updateSettingsReturnPage(route: AppRoute) {
        state.update { it.copy(settingsReturnPage = route) }
    }

    fun updateSelectedTab(index: Int) {
        if (index !in 0..3) return
        state.update { it.copy(selectedTab = index) }
    }
}
