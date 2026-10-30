package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import org.junit.Assert.*
import org.junit.Test

class FavoriteRowAnimationPolicyTest {
    @Test fun rowChangesAnimateAfterInitialListPositionRestore() {
        assertTrue(shouldAnimateFavoriteRowChanges(listPositionRestored = true, reduceMotion = false))
    }

    @Test fun initialListRestoreDoesNotAnimateRows() {
        assertFalse(shouldAnimateFavoriteRowChanges(listPositionRestored = false, reduceMotion = false))
    }

    @Test fun reducedMotionDisablesRowTransitions() {
        assertFalse(shouldAnimateFavoriteRowChanges(listPositionRestored = true, reduceMotion = true))
    }
}
