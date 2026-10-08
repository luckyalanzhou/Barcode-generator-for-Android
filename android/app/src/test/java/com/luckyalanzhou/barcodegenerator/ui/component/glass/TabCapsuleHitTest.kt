package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class TabSelectedCellHitTest {
    private fun contains(index: Int, point: Offset) = tabSelectedCellContains(360f, 60f, 4, index, point)

    @Test fun fullSelectedTabCellAcceptsTouchesOutsideTheVisibleCapsule() {
        assertTrue(contains(1, Offset(90f, 0f)))
        assertTrue(contains(1, Offset(179.9f, 60f)))
        assertTrue(contains(1, Offset(130f, 30f)))
        assertFalse(contains(1, Offset(89.9f, 30f)))
        assertFalse(contains(1, Offset(180f, 30f)))
    }

    @Test fun onlyTheCurrentlySelectedTabCellCanStartTheDrag() {
        assertTrue(contains(2, Offset(225f, 30f)))
        assertFalse(contains(2, Offset(135f, 30f)))
        assertFalse(contains(2, Offset(45f, 30f)))
    }

    @Test fun invalidGeometryAndCoordinatesAreRejected() {
        assertFalse(tabSelectedCellContains(0f, 60f, 4, 1, Offset(100f, 30f)))
        assertFalse(tabSelectedCellContains(360f, Float.NaN, 4, 1, Offset(100f, 30f)))
        assertFalse(tabSelectedCellContains(360f, 60f, 0, 0, Offset(100f, 30f)))
        assertFalse(tabSelectedCellContains(360f, 60f, 4, 4, Offset(350f, 30f)))
        assertFalse(contains(1, Offset(Float.NaN, 30f)))
        assertFalse(contains(1, Offset(100f, -1f)))
    }
}
