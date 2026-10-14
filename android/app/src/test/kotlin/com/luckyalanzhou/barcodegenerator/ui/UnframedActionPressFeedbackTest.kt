package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.component.UnframedPressAlpha
import com.luckyalanzhou.barcodegenerator.ui.component.UnframedPressScale
import com.luckyalanzhou.barcodegenerator.ui.component.unframedReleaseDelayMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnframedActionPressFeedbackTest {
    @Test fun shortTapsRemainVisibleForEightyMilliseconds() {
        assertEquals(80L, unframedReleaseDelayMillis(0L, 0L))
        assertEquals(60L, unframedReleaseDelayMillis(0L, 20_000_000L))
        assertEquals(1L, unframedReleaseDelayMillis(0L, 79_000_000L))
    }
    @Test fun longerPressesDoNotAddReleaseDelay() {
        assertEquals(0L, unframedReleaseDelayMillis(0L, 80_000_000L))
        assertEquals(0L, unframedReleaseDelayMillis(0L, 2_000_000_000L))
    }
    @Test fun feedbackIsStrongerThanOrdinaryButtonFeedback() {
        assertTrue(UnframedPressScale in .80f.. .90f)
        assertTrue(UnframedPressAlpha in .50f.. .65f)
    }
}
