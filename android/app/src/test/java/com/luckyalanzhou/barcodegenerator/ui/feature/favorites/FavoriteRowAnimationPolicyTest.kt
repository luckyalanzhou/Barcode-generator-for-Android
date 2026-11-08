package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import org.junit.Assert.*
import org.junit.Test

class FavoriteRowAnimationPolicyTest {
    @Test fun rowChangesAnimateOnlyWhileAnExplicitTreeChangeIsActive() {
        assertTrue(shouldAnimateFavoriteRowChanges(rowAnimationActive = true, reduceMotion = false))
    }

    @Test fun ordinaryListStateDoesNotAnimateRows() {
        assertFalse(shouldAnimateFavoriteRowChanges(rowAnimationActive = false, reduceMotion = false))
    }

    @Test fun reducedMotionDisablesRowTransitions() {
        assertFalse(shouldAnimateFavoriteRowChanges(rowAnimationActive = true, reduceMotion = true))
    }
}
