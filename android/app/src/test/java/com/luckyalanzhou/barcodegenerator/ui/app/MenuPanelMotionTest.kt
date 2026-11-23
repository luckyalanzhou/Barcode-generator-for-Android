package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuPanelMotionTest {
    @Test fun tabDragUsesTheSameScaleLeftAndRightAndAlsoCompressesDownward() {
        fun scale(x: Float, y: Float) = tabMenuDragScale(Offset(x, y), 8f, 64f)
        assertEquals(scale(-100f, 0f), scale(100f, 0f), 0f)
        assertEquals(scale(100f, 0f), scale(0f, 100f), 0f)
        assertTrue(scale(100f, 0f) < scale(40f, 0f))
        assertTrue(scale(1_000f, 0f) > .76f)
        assertEquals(1f, scale(3f, 4f), 0f)
        assertEquals(1f, scale(0f, -100f), 0f)
    }

    @Test fun tabDragOriginsAreMirroredAndInsetFromTheBottomCorners() {
        val left = tabMenuDragOrigin(leftSide = true)
        val right = tabMenuDragOrigin(leftSide = false)
        assertTrue(left.pivotFractionX > 0f && left.pivotFractionX < .5f)
        assertEquals(1f - left.pivotFractionX, right.pivotFractionX, .0001f)
        assertTrue(left.pivotFractionY > .5f && left.pivotFractionY < 1f)
        assertEquals(left.pivotFractionY, right.pivotFractionY, 0f)
    }

    @Test fun genericTabTitleIsOmittedButEditingTitlesRemain() {
        assertFalse(menuShowsTitle(""))
        assertTrue(menuShowsTitle("收藏备份"))
        assertTrue(menuShowsTitle("编辑"))
    }
}
