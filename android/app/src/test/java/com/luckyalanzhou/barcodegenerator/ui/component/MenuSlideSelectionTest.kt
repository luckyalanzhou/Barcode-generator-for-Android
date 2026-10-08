package com.luckyalanzhou.barcodegenerator.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class MenuSlideSelectionTest {
    private val bounds = mapOf(0 to Rect(0f, 40f, 160f, 80f), 1 to Rect(0f, 81f, 160f, 121f),
        2 to Rect(0f, 122f, 160f, 162f))

    @Test fun slideSwitchesSelectionAndReleaseChoosesOnlyLastRow() {
        val state = MenuSlideSelection()
        assertTrue(state.begin(Offset(20f, 60f), bounds))
        assertEquals(0, state.selected)
        state.move(Offset(20f, 100f), bounds)
        assertEquals(1, state.selected)
        state.move(Offset(20f, 140f), bounds)
        assertEquals(2, state.selected)
        assertEquals(2, state.release(Offset(20f, 140f), bounds))
        assertFalse(state.active)
        assertNull(state.selected)
        assertNull(state.release(Offset(20f, 140f), bounds))
    }

    @Test fun outsideReleaseDoesNotExecuteThePreviouslyHoveredRow() {
        val state = MenuSlideSelection()
        state.begin(Offset(20f, 140f), bounds)
        assertNull(state.release(Offset(170f, 140f), bounds))
    }

    @Test fun leavingAndReenteringRestoresTheCurrentRow() {
        val state = MenuSlideSelection()
        state.begin(Offset(20f, 60f), bounds)
        state.move(Offset(-1f, 100f), bounds)
        assertNull(state.selected)
        state.move(Offset(20f, 100f), bounds)
        assertEquals(1, state.selected)
    }

    @Test fun titleAndDividersCannotStartSelectionOrExecuteActions() {
        val state = MenuSlideSelection()
        assertFalse(state.begin(Offset(20f, 20f), bounds))
        assertNull(state.release(Offset(20f, 60f), bounds))
        assertFalse(state.begin(Offset(20f, 80.5f), bounds))
        state.begin(Offset(20f, 60f), bounds)
        assertNull(state.release(Offset(20f, 80.5f), bounds))
    }

    @Test fun cancellationClearsSelectionAndCannotExecute() {
        val state = MenuSlideSelection()
        state.begin(Offset(20f, 140f), bounds)
        state.cancel()
        assertNull(state.selected)
        assertFalse(state.active)
        assertNull(state.release(Offset(20f, 140f), bounds))
    }

    @Test fun releaseUsesUpdatedBoundsAfterAnimationOrScrolling() {
        val state = MenuSlideSelection()
        state.begin(Offset(20f, 60f), bounds)
        val moved = bounds.mapValues { (_, rect) -> rect.translate(Offset(0f, -41f)) }
        assertEquals(1, state.release(Offset(20f, 60f), moved))
    }

    @Test fun removedOrDisabledActionCannotExecute() {
        val state = MenuSlideSelection()
        state.begin(Offset(20f, 140f), bounds)
        assertNull(state.release(Offset(20f, 140f), bounds - 2))
    }
}
