package com.luckyalanzhou.barcodegenerator.ui.component

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MenuHapticsTest {
    @Test fun selectionUsesTheSoftestSupportedTickWithoutStrongFallback() {
        assertEquals(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, menuSelectionHapticType(34))
        assertNotEquals(menuOpenHapticType(34), menuSelectionHapticType(34))
        assertEquals(HapticFeedbackConstants.TEXT_HANDLE_MOVE, menuSelectionHapticType(33))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, menuSelectionHapticType(26))
    }

    @Test fun feedbackRequiresNewRowAndDebouncesRapidJitter() {
        assertTrue(shouldPerformMenuSelectionHaptic(null, 0, 180L, 100L))
        assertTrue(shouldPerformMenuSelectionHaptic(0, 1, 180L, 100L))
        assertFalse(shouldPerformMenuSelectionHaptic(0, 0, 500L, 100L))
        assertFalse(shouldPerformMenuSelectionHaptic(0, null, 500L, 100L))
        assertFalse(shouldPerformMenuSelectionHaptic(0, 1, 179L, 100L))
    }
    @Test fun modernOpeningUsesLightTickNotLongPressOrSoftSelectionTick() {
        for (api in listOf(34, 35, 37)) {
            assertEquals(HapticFeedbackConstants.SEGMENT_TICK, menuOpenHapticType(api))
            assertNotEquals(HapticFeedbackConstants.LONG_PRESS, menuOpenHapticType(api))
            assertNotEquals(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, menuOpenHapticType(api))
        }
    }

    @Test fun olderOpeningFallsBackToSingleClockTick() {
        for (api in listOf(26, 27, 30, 33)) {
            assertEquals(HapticFeedbackConstants.CLOCK_TICK, menuOpenHapticType(api))
        }
    }
}
