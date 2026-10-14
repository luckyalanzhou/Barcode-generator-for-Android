package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import org.junit.Assert.*
import org.junit.Test

class FavoriteRowAnimationPolicyTest {
    @Test fun initialLoadAndReturningRowsAreAlreadyVisible() {
        assertTrue(favoriteRowInitiallyVisible("folder-root", emptySet()))
        assertTrue(favoriteRowInitiallyVisible("group-old", setOf("group-new")))
    }
    @Test fun onlyNewlyExpandedRowsStartHidden() {
        assertFalse(favoriteRowInitiallyVisible("group-new", setOf("group-new")))
    }
    @Test fun finishedExpansionDoesNotReplayOnLaterScroll() {
        assertTrue(favoriteRowInitiallyVisible("group-new", emptySet()))
    }
}
