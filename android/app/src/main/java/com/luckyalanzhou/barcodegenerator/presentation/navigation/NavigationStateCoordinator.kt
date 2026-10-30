package com.luckyalanzhou.barcodegenerator.presentation.navigation

import com.luckyalanzhou.barcodegenerator.presentation.AppUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** 统一处理页面选择状态变更，同时保持 ViewModel 的公开 API 稳定。 */
internal class NavigationStateCoordinator(
    private val state: MutableStateFlow<AppUiState>,
) {
    fun navigateTo(route: NavigationRoute, fromTabSwipe: Boolean = false) {
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

    fun updateSettingsReturnPage(route: NavigationRoute) {
        state.update { it.copy(settingsReturnPage = route) }
    }

}
