package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class TabMenuPlacementTest {
    @Test fun topRowOpensBelowAndBottomTabStaysAbove() {
        val row = Rect(20f, 70f, 380f, 120f)
        val space = contextMenuSpace(row, Offset.Zero, 800f, 24f, 24f, 8f, 10f, 200f, false)
        assertFalse(space.above)
        val placement = tabMenuPlacement(row, Offset.Zero, IntSize(180, 200), 400f, 24f, 12f, 8f, 10f, space.above)
        assertEquals(128f, placement.top - 10f, 0f)
        assertTrue(placement.top - 10f + 200f < 776f)
        assertTrue(contextMenuSpace(Rect(20f, 700f, 80f, 760f), Offset.Zero,
            800f, 24f, 24f, 8f, 10f, 200f, true).above)
    }

    @Test fun lowerRowCanUseSpaceAboveWithoutOverlappingStatusBar() {
        val row = Rect(20f, 620f, 380f, 670f)
        val space = contextMenuSpace(row, Offset.Zero, 800f, 24f, 24f, 8f, 10f, 200f, false)
        assertTrue(space.above)
        assertTrue(space.height >= 200f)
    }

    @Test fun usableSpaceAccountsForWindowOriginAndNavigationInset() {
        val row = Rect(20f, 90f, 380f, 140f)
        val space = contextMenuSpace(row, Offset(0f, 20f), 800f, 24f, 48f, 8f, 10f, 200f, false)
        assertFalse(space.above)
        assertEquals(606f, space.height, 0f)
    }

    @Test fun tinyWindowsNeverReportNegativeCapacity() {
        val space = contextMenuSpace(Rect(0f, 20f, 60f, 70f), Offset.Zero,
            80f, 24f, 48f, 8f, 10f, 200f, false)
        assertTrue(space.height >= 0f)
    }
    @Test fun leftAndRightTabsStayOnScreenAndMenuTracksTheirCenters() {
        for (x in listOf(20f, 120f, 220f, 320f)) {
            val result = tabMenuPlacement(Rect(x, 700f, x + 60f, 760f), Offset.Zero,
                IntSize(180, 140), 400f, 24f, 12f, 8f, 10f)
            assertTrue(result.left >= 12f)
            assertTrue(result.left + 180f <= 388f)
            assertEquals(552f, result.top, 0f)
            assertTrue(result.pivotX in 0f..1f)
            assertEquals(x + 30f, result.anchorCenterX, 0f)
        }
    }

    @Test fun windowOriginAndLiftAreIncludedInSafePlacement() {
        val result = tabMenuPlacement(Rect(100f, 300f, 160f, 360f), Offset(10f, 20f),
            IntSize(180, 300), 220f, 24f, 12f, 8f, 10f)
        assertEquals(42f, result.top, 0f)
        assertTrue(result.top - 10f >= 32f)
        assertEquals(120f, result.anchorCenterX, 0f)
    }
}
