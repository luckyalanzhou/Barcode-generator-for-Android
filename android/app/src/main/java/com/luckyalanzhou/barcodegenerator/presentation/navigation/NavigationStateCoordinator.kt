package com.luckyalanzhou.barcodegenerator.presentation.navigation

import com.luckyalanzhou.barcodegenerator.presentation.AppUiState
import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Owns page selection mutations while keeping the ViewModel's public API stable. */
internal class NavigationStateCoordinator(
    private val state: MutableStateFlow<AppUiState>,
) {
    fun navigateTo(route: AppRoute, fromTabSwipe: Boolean = false) {
        state.update {
            it.copy(
                page = route,
                tabChangeFromSwipe = fromTabSwipe && route.mainTabIndex != null,
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

}
