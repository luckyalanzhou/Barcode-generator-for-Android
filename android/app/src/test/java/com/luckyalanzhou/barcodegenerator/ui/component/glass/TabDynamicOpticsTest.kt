package com.luckyalanzhou.barcodegenerator.ui.component.glass

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
        // Dynamic optics saturate at 5 tabs/s; use the saturation point for max-value assertions.
        val fast = tabDynamicOptics(frame(5f))
        assertEquals(1.25f, fast.dispersionPx, 0f)
        assertEquals(.08f, fast.edgeColorStrength, 0f)
        assertEquals(fast, tabDynamicOptics(frame(-5f)))
        assertEquals(fast, tabDynamicOptics(frame(100f, density = 3f)))
        assertTrue(tabDynamicOptics(frame(.00001f)).dispersionPx < .001f)
    }

    @Test fun capsuleDispersionRampsWithRealisticDragSpeed() {
        val slow = tabDynamicOptics(frame(.06f)).dispersionPx
        val medium = tabDynamicOptics(frame(1f)).dispersionPx
        val fast = tabDynamicOptics(frame(4f)).dispersionPx

        assertTrue("slow drags should keep chromatic dispersion subtle", slow < fast * .1f)
        assertTrue("medium drags should increase the edge lensing", medium > slow)
        assertTrue("fast drags should reach a stronger optical response", fast > medium)
    }

    @Test fun touchStrainIsSmallSymmetricAndClearsAtRest() {
        assertEquals(0f, tabContactStrain(null, 100f, 90f, 1f), 0f)
        assertEquals(0f, tabContactStrain(145f, 100f, 90f, 0f), 0f)
        assertEquals(0f, tabContactStrain(Float.NaN, 100f, 90f, 1f), 0f)
        assertEquals(.02f, tabContactStrain(10000f, 100f, 90f, 1f), 0f)
        assertEquals(tabContactStrain(120f, 100f, 90f, 1f),
            tabContactStrain(80f, 100f, 90f, 1f), 0f)
    }

    @Test fun strainedFrameKeepsOneShapeWithSmallTrackEdgeOverhang() {
        val maxOverhang = 3f
        for (progress in listOf(0f, .5f, 3f)) {
            val rest = tabGlassFrame(
                360f, 60f, 1f, 4, progress, 0f, 0f, 1f, touchX = 10000f,
                velocityTabsPerSecond = 0f,
            )
            val pressed = tabGlassFrame(
                360f, 60f, 1f, 4, progress, 1f, 0f, 1f, touchX = 10000f,
                velocityTabsPerSecond = 0f,
            )
            assertTrue(pressed.halfWidth > rest.halfWidth)
            assertTrue(pressed.halfHeight > rest.halfHeight)
            assertTrue(pressed.centerX - pressed.halfWidth >= -maxOverhang)
            assertTrue(pressed.centerX + pressed.halfWidth <= pressed.width + maxOverhang)
            assertTrue(pressed.centerY - pressed.halfHeight >= -maxOverhang)
            assertTrue(pressed.centerY + pressed.halfHeight <= pressed.height + maxOverhang)
        }
    }
}
