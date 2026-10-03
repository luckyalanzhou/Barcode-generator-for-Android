package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGlassFrameTest {
    @Test
    fun capsuleStaysInsideSceneAcrossTheWholeDrag() {
        for (width in listOf(0f, 1f, 64f, 360f, 1080f)) {
            for (height in listOf(0f, 1f, 32f, 64f, 192f)) {
                for (step in -10..40) {
                    val frame = tabGlassFrame(width, height, 3f, 4, step / 10f, 1f, .035f, 1f)
                    assertTrue(frame.centerX - frame.halfWidth >= -.0001f)
                    assertTrue(frame.centerX + frame.halfWidth <= frame.width + .0001f)
                    assertTrue(frame.centerY - frame.halfHeight >= -.0001f)
                    assertTrue(frame.centerY + frame.halfHeight <= frame.height + .0001f)
                }
            }
        }
    }

    @Test
    fun restingLensHasNoDisplacementRegardlessOfTabPosition() {
        for (progress in listOf(0f, .3f, 1f, 1.9f, 3f)) {
            val frame = tabGlassFrame(360f, 60f, 1f, 4, progress, 0f, 0f, 1f)
            assertEquals(0f, frame.motion, 0f)
            assertEquals(0f, frame.refractionPx, 0f)
            assertEquals(28f, frame.halfHeight, .001f)
        }
    }

    @Test
    fun pixelRefractionScalesWithDensityAndIsBounded() {
        val one = tabGlassFrame(360f, 60f, 1f, 4, 1f, 1f, 0f, 1f, refractionDp = 2f)
        val three = tabGlassFrame(1080f, 180f, 3f, 4, 1f, 1f, 0f, 1f, refractionDp = 2f)
        assertEquals(one.refractionPx * 3f, three.refractionPx, .001f)
        val limited = tabGlassFrame(360f, 60f, 1f, 4, 1f, 99f, 99f, 1f, refractionDp = 99f)
        assertEquals(3f, limited.refractionPx, 0f)
        assertEquals(1f, limited.motion, 0f)
    }
}
