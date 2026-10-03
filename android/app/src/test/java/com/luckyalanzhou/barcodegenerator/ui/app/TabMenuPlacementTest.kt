package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class TabMenuPlacementTest {
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
