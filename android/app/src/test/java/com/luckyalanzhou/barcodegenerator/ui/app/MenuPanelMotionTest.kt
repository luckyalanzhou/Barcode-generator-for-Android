package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class MenuPanelMotionTest {
    private val panel = Rect(20f, 100f, 220f, 300f)

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
