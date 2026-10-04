package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import com.luckyalanzhou.barcodegenerator.ui.app.isTabReselection
import com.luckyalanzhou.barcodegenerator.ui.theme.bottomTabHeightForLabel
import org.junit.Assert.*
import org.junit.Test

class TabSelectionPolicyTest {
    @Test fun switchingPagesDoesNotResetDestination() {
        assertFalse(isTabReselection(AppRoute.Generate, AppRoute.History, false))
        assertFalse(isTabReselection(AppRoute.History, AppRoute.Favorites, false))
    }
    @Test fun tappingCurrentHistoryOrFavoritesRunsSecondaryAction() {
        assertTrue(isTabReselection(AppRoute.History, AppRoute.History, false))
        assertTrue(isTabReselection(AppRoute.Favorites, AppRoute.Favorites, false))
    }
    @Test fun swipeCompletionDoesNotActAsReselection() {
        assertFalse(isTabReselection(AppRoute.History, AppRoute.History, true))
        assertFalse(isTabReselection(AppRoute.Favorites, AppRoute.Favorites, true))
    }
    @Test fun leavingResultsDoesNotResetSourceList() {
        assertFalse(isTabReselection(AppRoute.Results, AppRoute.History, false))
        assertFalse(isTabReselection(AppRoute.Results, AppRoute.Favorites, false))
        assertFalse(isTabReselection(AppRoute.Results, AppRoute.Results, false))
    }
    @Test fun scaledLabelsGrowBarWithoutShrinkingDefaultTouchTargets() {
        assertEquals(72f, bottomTabHeightForLabel(24f), 0f)
        assertEquals(72f, bottomTabHeightForLabel(12f), 0f)
        assertEquals(96f, bottomTabHeightForLabel(48f), 0f)
        assertEquals(72f, bottomTabHeightForLabel(Float.NaN), 0f)
    }
}
