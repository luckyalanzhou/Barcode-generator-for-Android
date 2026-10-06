package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGlassFrameTest {
    @Test fun fasterMotionHasBoundedStrainAndOpticsWithoutDistortingForeground() {
        val slow = tabGlassFrame(360f, 60f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = 0f)
        val fast = tabGlassFrame(360f, 60f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = 4f)
        val capped = tabGlassFrame(360f, 60f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = 100f)
        assertTrue(fast.refractionPx > slow.refractionPx)
        assertTrue(fast.halfWidth > slow.halfWidth)
        assertEquals(fast, capped)
        assertEquals(fast, tabGlassFrame(360f, 60f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = -4f))
    }

    @Test fun reducedMotionSuppressesOpticsEvenWithHighVelocity() {
        val frame = tabGlassFrame(360f, 60f, 1f, 4, .5f, 0f, 0f, 1f, velocityTabsPerSecond = 100f)
        assertEquals(0f, frame.refractionPx, 0f)
        assertEquals(0f, frame.motion, 0f)
        assertEquals(42f, frame.halfWidth, .001f)
    }
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
        assertEquals(4.4f, limited.refractionPx, 0f)
        assertEquals(1f, limited.motion, 0f)
    }

    @Test fun slowMotionRetainsEightyPercentOfDynamicRefraction() {
        val slow = tabGlassFrame(360f, 60f, 1f, 4, .5f, 1f, 0f, 1f,
            refractionDp = 3f, velocityTabsPerSecond = 0f)
        assertEquals(2.4f, slow.refractionPx, .0001f)
        assertEquals(.8f, slow.motion, .0001f)
    }

    @Test fun capsuleEdgeRevealsOnlyTheCoveredPartOfNeighboringGlyphs() {
        val beforeContact = tabGlassFrame(360f, 60f, 1f, 4, 1.38f, 0f, 0f, 1f)
        val edgeContact = tabGlassFrame(360f, 60f, 1f, 4, 1.4f, 0f, 0f, 1f)
        val beforeBounds = tabGlassCapsuleBoundsInTab(beforeContact, tabIndex = 2, tabCount = 4)!!
        val contactBounds = tabGlassCapsuleBoundsInTab(edgeContact, tabIndex = 2, tabCount = 4)!!

        // The neighboring icon begins 32 px from this cell's left edge.
        assertTrue(beforeBounds.right < 32f)
        assertTrue(contactBounds.right >= 32f)
        assertTrue(contactBounds.right < 58f)
    }
}
