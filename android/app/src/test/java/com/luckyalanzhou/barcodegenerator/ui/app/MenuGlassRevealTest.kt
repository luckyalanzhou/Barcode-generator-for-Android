package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.*
import org.junit.Test

class MenuGlassRevealTest {
    @Test fun bothMenuTypesGrowContinuouslyWithoutChangingFinalGeometry() {
        for (tab in listOf(false, true)) {
            var previous = menuGlassReveal(0f, tab, false)
            for (step in 1..100) {
                val frame = menuGlassReveal(step / 100f, tab, false)
                assertTrue(frame.scale >= previous.scale)
                assertTrue(frame.thickness >= previous.thickness)
                assertTrue(frame.shadow >= previous.shadow)
                previous = frame
            }
            assertEquals(MenuGlassReveal(1f, 1f, 1f, 1f), previous)
            assertEquals(1f, menuGlassReveal(.2f, tab, false).alpha, 0f)
        }
    }

    @Test fun reducedMotionHasNoScaleOrThicknessAnimation() {
        for (p in listOf(.01f, .5f, 1f)) {
            assertEquals(MenuGlassReveal(1f, p, 1f, 1f), menuGlassReveal(p, true, true))
        }
        assertEquals(0f, menuGlassReveal(0f, true, true).alpha, 0f)
    }

    @Test fun invalidProgressAndOvershootAreBounded() {
        assertEquals(menuGlassReveal(0f, false, false), menuGlassReveal(Float.NaN, false, false))
        assertEquals(menuGlassReveal(1f, true, false), menuGlassReveal(2f, true, false))
        assertEquals(menuGlassReveal(0f, true, false), menuGlassReveal(-1f, true, false))
    }
}
