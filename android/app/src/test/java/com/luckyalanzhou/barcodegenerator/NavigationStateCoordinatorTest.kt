package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.presentation.AppUiState
import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationStateCoordinator
import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStateCoordinatorTest {
    @Test
    fun mainRouteUpdatesSelectedTabAndPreservesItForSecondaryPages() {
        val state = MutableStateFlow(AppUiState())
        val coordinator = NavigationStateCoordinator(state)

        coordinator.navigateTo(AppRoute.History)
        assertEquals(1, state.value.selectedTab)

        coordinator.navigateTo(AppRoute.Results)
        assertEquals(AppRoute.Results, state.value.page)
        assertEquals(1, state.value.selectedTab)
    }

    @Test
    fun secondaryRoutePreservesSelectedTab() {
        val state = MutableStateFlow(AppUiState())
        NavigationStateCoordinator(state).navigateTo(AppRoute.Results)

        assertEquals(0, state.value.selectedTab)
    }
}
