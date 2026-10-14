package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPageTransitionTest {
    @Test
    fun resultPageTransitionsRemainDisabled() {
        assertEquals(AppPageTransitionKind.NONE, appPageTransitionKind(AppRoute.Generate, AppRoute.Results, false))
        assertEquals(AppPageTransitionKind.NONE, appPageTransitionKind(AppRoute.Results, AppRoute.Favorites, false))
    }

    @Test
    fun mainTabTransitionsReflectTheNavigationGesture() {
        assertEquals(AppPageTransitionKind.TAB_SWIPE, appPageTransitionKind(AppRoute.History, AppRoute.Favorites, true))
        assertEquals(AppPageTransitionKind.TAB_SELECTION, appPageTransitionKind(AppRoute.Generate, AppRoute.History, false))
    }

    @Test
    fun secondaryPagesUseOneStableTransitionKind() {
        assertEquals(AppPageTransitionKind.SECONDARY_PAGE, appPageTransitionKind(AppRoute.Settings, AppRoute.LanShare, false))
        assertEquals(AppPageTransitionKind.SECONDARY_PAGE, appPageTransitionKind(AppRoute.LanShare, AppRoute.Settings, false))
    }

    @Test
    fun unchangedRouteDoesNotAnimate() {
        assertEquals(AppPageTransitionKind.NONE, appPageTransitionKind(AppRoute.Generate, AppRoute.Generate, true))
    }

    @Test fun everyMainTabIncludingHistoryUsesTheSameTransitionPolicy() {
        val tabs = listOf(AppRoute.Generate, AppRoute.History, AppRoute.Favorites, AppRoute.Settings)
        tabs.forEach { from -> tabs.filter { it != from }.forEach { to ->
            assertEquals(AppPageTransitionKind.TAB_SELECTION, appPageTransitionKind(from, to, false))
            assertEquals(AppPageTransitionKind.TAB_SWIPE, appPageTransitionKind(from, to, true))
        } }
    }
}
