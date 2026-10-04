package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class TabCapsuleHitTest {
    private fun frame(progress: Float = 1f) = tabGlassFrame(360f, 60f, 1f, 4, progress, 0f, 0f, 0f)

    @Test fun selectedCapsuleAcceptsItsCenterButNotOtherTabs() {
        val f = frame()
        assertTrue(tabCapsuleContains(f, Offset(f.centerX, f.centerY)))
        assertFalse(tabCapsuleContains(f, Offset(45f, 30f)))
        assertFalse(tabCapsuleContains(f, Offset(225f, 30f)))
    }
    @Test fun roundedCornersAndOuterPaddingAreNotDraggable() {
        val f = frame()
        assertFalse(tabCapsuleContains(f, Offset(f.centerX - f.halfWidth, f.centerY - f.halfHeight)))
        assertFalse(tabCapsuleContains(f, Offset(f.centerX, 0f)))
        assertFalse(tabCapsuleContains(f, Offset(Float.NaN, 30f)))
    }
    @Test fun hitAreaTracksCurrentAnimatedPositionRatherThanOldSelection() {
        val f = frame(1.5f)
        assertTrue(tabCapsuleContains(f, Offset(180f, 30f)))
        assertFalse(tabCapsuleContains(f, Offset(100f, 30f)))
    }
}
