package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.*
import org.junit.Test

class MenuGlassRevealTest {
    @Test fun completePanelGrowsContinuouslyWithoutEarlyFullOpacity() {
            var previous = menuGlassReveal(0f, false)
            for (step in 1..100) {
                val frame = menuGlassReveal(step / 100f, false)
                assertTrue(frame.scale >= previous.scale)
                assertTrue(frame.thickness >= previous.thickness)
                assertTrue(frame.shadow >= previous.shadow)
                previous = frame
            }
            assertEquals(MenuGlassReveal(1f, 1f, 1f, 1f), previous)
            // 展开初期不再突然完全显现，完整轮廓的透明度保持连续。
            assertTrue(menuGlassReveal(.2f, false).alpha < .5f)
    }

    @Test fun reducedMotionHasNoScaleOrThicknessAnimation() {
        for (p in listOf(.01f, .5f, 1f)) {
            assertEquals(MenuGlassReveal(1f, p, 1f, 1f), menuGlassReveal(p, true))
        }
        assertEquals(0f, menuGlassReveal(0f, true).alpha, 0f)
    }

    @Test fun invalidProgressAndOvershootAreBounded() {
        assertEquals(menuGlassReveal(0f, false), menuGlassReveal(Float.NaN, false))
        val overshoot = menuGlassReveal(2f, false)
        assertTrue(overshoot.scale in 1f..1.015f)
        assertEquals(1f, overshoot.alpha, 0f)
        assertEquals(1f, overshoot.thickness, 0f)
        assertEquals(menuGlassReveal(0f, false), menuGlassReveal(-1f, false))
    }
}
