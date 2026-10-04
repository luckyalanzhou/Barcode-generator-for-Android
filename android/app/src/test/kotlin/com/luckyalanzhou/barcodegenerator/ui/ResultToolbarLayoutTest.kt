package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.feature.results.resultToolbarActionWidth
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultToolbarLayoutTest {
    @Test fun normalTextKeepsExistingActionWidth() {
        assertEquals(64f, resultToolbarActionWidth(1f), 0f)
    }

    @Test fun largerTextGetsMoreLabelSpace() {
        assertEquals(80f, resultToolbarActionWidth(1.25f), 0f)
    }

    @Test fun extremeFontScaleKeepsTwoActionGroupWithinPhoneWidth() {
        assertEquals(96f, resultToolbarActionWidth(3f), 0f)
    }

    @Test fun smallOrInvalidScaleDoesNotShrinkTouchTargets() {
        listOf(0.5f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals(64f, resultToolbarActionWidth(it), 0f)
        }
    }
}
