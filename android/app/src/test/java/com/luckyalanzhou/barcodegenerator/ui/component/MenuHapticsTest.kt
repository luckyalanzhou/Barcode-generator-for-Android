package com.luckyalanzhou.barcodegenerator.ui.component

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MenuHapticsTest {
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
