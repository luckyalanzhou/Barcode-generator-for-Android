package com.luckyalanzhou.barcodegenerator.presentation.navigation

import com.luckyalanzhou.barcodegenerator.presentation.AppUiState
import kotlinx.coroutines.flow.MutableStateFlow

/** 路由状态的唯一业务入口；不向 Compose 暴露 NavController。 */
internal class AppRouteStateFacade(
    state: MutableStateFlow<AppUiState>,
) {
    private val coordinator = NavigationStateCoordinator(state)

    fun navigateTo(route: NavigationRoute, fromTabSwipe: Boolean = false) =
        coordinator.navigateTo(route, fromTabSwipe)

    fun updateSettingsReturnPage(route: NavigationRoute) =
        coordinator.updateSettingsReturnPage(route)
}
