package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.*
import org.junit.Test

class TabDynamicOpticsTest {
    private fun frame(velocity: Float, motion: Float = 1f, density: Float = 1f) =
        tabGlassFrame(360f * density, 60f * density, density, 4, .5f, motion, 0f, 1f,
            velocityTabsPerSecond = velocity)

    @Test fun restingPressedAndReducedMotionHaveNoExtraOptics() {
        for (value in listOf(frame(0f), frame(4f, 0f), frame(Float.NaN))) {
            assertEquals(TabDynamicOptics(0f, 0f), tabDynamicOptics(value))
        }
    }

    @Test fun dispersionUsesPhysicalPixelsAndRemainsBounded() {
        val fast = tabDynamicOptics(frame(4f))
        assertEquals(.75f, fast.dispersionPx, 0f)
        assertEquals(.055f, fast.edgeColorStrength, 0f)
        assertEquals(fast, tabDynamicOptics(frame(-4f)))
        assertEquals(fast, tabDynamicOptics(frame(100f, density = 3f)))
        assertTrue(tabDynamicOptics(frame(.00001f)).dispersionPx < .001f)
    }

    @Test fun touchStrainIsSmallSymmetricAndClearsAtRest() {
        assertEquals(0f, tabContactStrain(null, 100f, 90f, 1f), 0f)
        assertEquals(0f, tabContactStrain(145f, 100f, 90f, 0f), 0f)
        assertEquals(0f, tabContactStrain(Float.NaN, 100f, 90f, 1f), 0f)
        assertEquals(.02f, tabContactStrain(10000f, 100f, 90f, 1f), 0f)
        assertEquals(tabContactStrain(120f, 100f, 90f, 1f),
            tabContactStrain(80f, 100f, 90f, 1f), 0f)
    }

    @Test fun strainedFrameStaysInsideViewportAndSharesOneShape() {
        for (progress in listOf(0f, .5f, 3f)) {
            val rest = tabGlassFrame(360f, 60f, 1f, 4, progress, 0f, 0f, 1f, touchX = 10000f)
            val pressed = tabGlassFrame(360f, 60f, 1f, 4, progress, 1f, 0f, 1f, touchX = 10000f)
            assertTrue(pressed.halfWidth > rest.halfWidth)
            assertTrue(pressed.halfHeight < rest.halfHeight)
            assertTrue(pressed.centerX - pressed.halfWidth >= 0f)
            assertTrue(pressed.centerX + pressed.halfWidth <= pressed.width)
        }
    }
}
