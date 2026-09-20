package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.ui.AppRoute
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
    fun invalidTabIsIgnored() {
        val state = MutableStateFlow(AppUiState())
        NavigationStateCoordinator(state).updateSelectedTab(4)

        assertEquals(0, state.value.selectedTab)
    }
}
