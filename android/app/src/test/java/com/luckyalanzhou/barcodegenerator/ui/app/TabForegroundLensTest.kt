package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.*
import org.junit.Test

class TabForegroundLensTest {
    private fun frame(density: Float = 1f, motion: Float = 1f) =
        tabGlassFrame(360f * density, 60f * density, density, 4, .5f, motion, 0f, 1f)

    @Test fun stationaryAndDisabledOpticsNeverDistortForeground() {
        assertEquals(0f, tabForegroundDisplacement(frame(), 0f), 0f)
        assertEquals(0f, tabForegroundDisplacement(frame(motion = 0f), 4f), 0f)
        assertEquals(0f, tabForegroundDisplacement(frame(), Float.NaN), 0f)
    }

    @Test fun displacementIsBoundedAndScalesInPhysicalPixels() {
        val one = tabForegroundDisplacement(frame(), 4f)
        assertTrue(one > 0f && one <= 1.2f)
        assertEquals(one * 3f, tabForegroundDisplacement(frame(3f), 4f), .0001f)
        assertEquals(one, tabForegroundDisplacement(frame(), -4f), 0f)
        assertEquals(one, tabForegroundDisplacement(frame(), 100f), 0f)
    }

    @Test fun slowdownContinuouslyReturnsToIdentity() {
        var previous = tabForegroundDisplacement(frame(), .8f)
        for (step in 79 downTo 0) {
            val current = tabForegroundDisplacement(frame(), step / 100f)
            assertTrue(current <= previous)
            previous = current
        }
        assertEquals(0f, previous, 0f)
    }
}
