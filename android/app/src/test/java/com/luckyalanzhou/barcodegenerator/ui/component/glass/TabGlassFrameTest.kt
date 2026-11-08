package com.luckyalanzhou.barcodegenerator.ui.component.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGlassFrameTest {
    @Test fun fasterMotionChangesRefractionWithoutChangingCapsuleSize() {
        val slow = tabGlassFrame(360f, 100f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = 0f)
        val fast = tabGlassFrame(360f, 100f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = 10f)
        val capped = tabGlassFrame(360f, 100f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = 100f)
        assertTrue(fast.refractionPx > slow.refractionPx)
        assertEquals(slow.halfWidth, fast.halfWidth, .001f)
        assertEquals(slow.halfHeight, fast.halfHeight, .001f)
        assertEquals(fast, capped)
        assertEquals(capped, tabGlassFrame(360f, 100f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = 100f))
        val reverse = tabGlassFrame(360f, 100f, 1f, 4, .5f, 1f, 0f, 1f, velocityTabsPerSecond = -10f)
        assertEquals(fast.refractionPx, reverse.refractionPx, 0f)
        assertTrue(reverse.centerX - reverse.halfWidth >= 0f)
        assertTrue(reverse.centerX + reverse.halfWidth <= reverse.width)
    }

    @Test fun zeroOpticalActivitySuppressesRefractionEvenWithHighVelocity() {
        val frame = tabGlassFrame(360f, 60f, 1f, 4, .5f, 0f, 0f, 1f, velocityTabsPerSecond = 100f)
        assertEquals(0f, frame.refractionPx, 0f)
        assertEquals(0f, frame.motion, 0f)
        assertEquals(44f, frame.halfWidth, .001f)
    }
    @Test
    fun capsuleHasOnlySmallIntentionalOverhangAtTrackEdges() {
        for ((width, density) in listOf(360f to 1f, 1080f to 3f)) {
            val height = 60f * density
            val maxOverhang = 3f * density
            for (step in -10..40) {
                val frame = tabGlassFrame(width, height, density, 4, step / 10f, 1f, .035f, 1f)
                assertTrue(frame.centerX - frame.halfWidth >= -maxOverhang)
                assertTrue(frame.centerX + frame.halfWidth <= frame.width + maxOverhang)
                assertTrue(frame.centerY - frame.halfHeight >= -maxOverhang)
                assertTrue(frame.centerY + frame.halfHeight <= frame.height + maxOverhang)
            }
        }
    }

    @Test
    fun restingLensHasNoDisplacementRegardlessOfTabPosition() {
        for (progress in listOf(0f, .3f, 1f, 1.9f, 3f)) {
            val frame = tabGlassFrame(360f, 60f, 1f, 4, progress, 0f, 0f, 1f, velocityTabsPerSecond = 0f)
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
        val limited = tabGlassFrame(
            360f, 60f, 1f, 4, 1f, 99f, 99f, 1f,
            refractionDp = 99f, velocityTabsPerSecond = 100f,
        )
        assertEquals(3.0f, limited.refractionPx, 0f)
        assertEquals(1f, limited.motion, 0f)
    }

    @Test fun slowMotionRetainsEightyPercentOfDynamicRefraction() {
        val slow = tabGlassFrame(360f, 60f, 1f, 4, .5f, 1f, 0f, 1f,
            refractionDp = 3f, velocityTabsPerSecond = 0f)
        assertEquals(2.4f, slow.refractionPx, .0001f)
        assertEquals(.8f, slow.motion, .0001f)
    }

    @Test fun capsuleEdgeRevealsOnlyTheCoveredPartOfNeighboringGlyphs() {
        val beforeContact = tabGlassFrame(360f, 60f, 1f, 4, 1.36f, 0f, 0f, 1f, velocityTabsPerSecond = 0f)
        val edgeContact = tabGlassFrame(360f, 60f, 1f, 4, 1.38f, 0f, 0f, 1f, velocityTabsPerSecond = 0f)
        val beforeBounds = tabGlassCapsuleBoundsInTab(beforeContact, tabIndex = 2, tabCount = 4)!!
        val contactBounds = tabGlassCapsuleBoundsInTab(edgeContact, tabIndex = 2, tabCount = 4)!!

        // 相邻图标的起点距离当前单元格左边缘 32 px。
        assertTrue(beforeBounds.right < 32f)
        assertTrue(contactBounds.right >= 32f)
        assertTrue(contactBounds.right < 58f)
    }
}
