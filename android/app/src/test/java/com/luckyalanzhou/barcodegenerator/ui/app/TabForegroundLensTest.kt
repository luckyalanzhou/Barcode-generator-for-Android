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
        assertTrue(one > 2.2f && one <= 2.8f)
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

    @Test fun slowDragIsVisibleWithoutADiscontinuityAtRest() {
        val slowFrame = tabGlassFrame(360f, 60f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = .1f)
        assertTrue(tabForegroundDisplacement(slowFrame, .1f) >= .5f)
        assertEquals(tabForegroundDisplacement(slowFrame, .1f), tabForegroundDisplacement(slowFrame, -.1f), 0f)
        assertTrue(tabForegroundDisplacement(slowFrame, .00001f) < .001f)
        assertEquals(0f, tabForegroundDisplacement(slowFrame, 0f), 0f)
    }

    @Test fun strongerRefractionCannotExceedForegroundLimit() {
        for (density in listOf(1f, 2f, 3f)) {
            val strong = tabGlassFrame(360f * density, 60f * density, density, 4,
                .5f, 1f, 0f, 1f, refractionDp = 99f)
            assertEquals(2.8f * density, tabForegroundDisplacement(strong, 4f), .0001f)
            assertEquals(0f, tabForegroundDisplacement(strong, 0f), 0f)
        }
    }
}
