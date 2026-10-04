package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class MenuPanelMotionTest {
    private val panel = Rect(20f, 100f, 220f, 300f)

    @Test fun sourceDragStartsAtRestAndFollowsAllFourDirections() {
        val origin = Offset(100f, 400f)
        assertEquals(Offset.Zero, menuAnchorMotion(origin, origin, 64f))
        assertEquals(Offset(1f / 3f, -1f / 3f), menuAnchorMotion(origin + Offset(32f, -32f), origin, 64f))
        assertEquals(Offset(-1f / 3f, 1f / 3f), menuAnchorMotion(origin + Offset(-32f, 32f), origin, 64f))
        assertEquals(Offset(1000f / 1064f, -1000f / 1064f), menuAnchorMotion(origin + Offset(1000f, -1000f), origin, 64f))
        assertEquals(Offset.Zero, menuAnchorMotion(null, origin, 64f))
        assertEquals(Offset.Zero, menuAnchorMotion(origin, origin, 0f))
    }

    @Test fun largerSourceDragsKeepRespondingAndShrinkInsteadOfSwelling() {
        val short = menuAnchorMotion(Offset(64f, 0f), Offset.Zero, 64f)
        val long = menuAnchorMotion(Offset(192f, 0f), Offset.Zero, 64f)
        assertTrue(long.x > short.x)
        assertTrue(long.x < 1f)
        assertEquals(1f, menuDragScale(Offset.Zero), .0001f)
        assertTrue(menuDragScale(long) < menuDragScale(short))
        assertEquals(.8f, menuDragScale(Offset(1f, 1f)), .0001f)
    }

    @Test fun noTouchAndPanelCenterAreAtRest() {
        assertEquals(Offset.Zero, menuPanelMotion(null, panel))
        assertEquals(Offset.Zero, menuPanelMotion(panel.center, panel))
    }

    @Test fun followsDirectionAndDistanceWithinPanel() {
        assertEquals(Offset(.5f, -.5f), menuPanelMotion(Offset(170f, 150f), panel))
    }

    @Test fun outsideTouchesCannotCauseUnboundedMotion() {
        assertEquals(Offset(1f, -1f), menuPanelMotion(Offset(10000f, -10000f), panel))
        assertEquals(Offset(-1f, 1f), menuPanelMotion(Offset(-10000f, 10000f), panel))
    }

    @Test fun zeroSizedPanelNeverProducesInvalidValues() {
        assertEquals(Offset.Zero, menuPanelMotion(Offset(1f, 2f), Rect.Zero))
    }

    @Test fun windowTranslationDoesNotChangeRelativeMotion() {
        val offset = Offset(24f, 38f)
        val point = Offset(170f, 150f)
        assertEquals(menuPanelMotion(point, panel), menuPanelMotion(point + offset, panel.translate(offset)))
    }
}
