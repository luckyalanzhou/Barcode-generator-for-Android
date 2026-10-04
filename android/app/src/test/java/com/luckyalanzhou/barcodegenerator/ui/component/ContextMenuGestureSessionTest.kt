package com.luckyalanzhou.barcodegenerator.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class ContextMenuGestureSessionTest {
    private val bounds = mapOf(0 to Rect(10f, 100f, 200f, 148f), 1 to Rect(10f, 149f, 200f, 197f))

    @Test fun draggingWithinSourceIconThenReleasingKeepsMenuOpen() {
        val session = longPress()
        session.sourceBounds = Rect(60f, 220f, 140f, 280f)
        session.move(Offset(120f, 270f), 8f, bounds)
        assertEquals(ContextMenuRelease.KeepOpen, session.release(Offset(120f, 270f), bounds))
        assertNull(session.feedbackPoint)
    }

    @Test fun bothNearbyAndDistantSourceReleasesKeepMenuOpen() {
        val session = longPress()
        session.sourceBounds = Rect(60f, 220f, 140f, 280f)
        session.sourceDragMarginPx = 48f
        session.move(Offset(170f, 290f), 8f, bounds)
        assertEquals(ContextMenuRelease.KeepOpen, session.release(Offset(170f, 290f), bounds))
        val outside = longPress()
        outside.sourceBounds = session.sourceBounds
        outside.sourceDragMarginPx = 48f
        outside.move(Offset(300f, 350f), 8f, bounds)
        assertEquals(ContextMenuRelease.KeepOpen, outside.release(Offset(300f, 350f), bounds))
        assertTrue(outside.menuOpen)
        assertNull(outside.feedbackPoint)
        assertFalse(outside.gesture.active)
    }

    private fun longPress(): ContextMenuGestureSession = ContextMenuGestureSession().apply {
        pointerDown = true
        lastPointer = Offset(100f, 250f)
        open()
        ready = true
    }

    @Test fun existingFingerCanEnterPanelWithoutAnotherDown() {
        val session = longPress()
        assertTrue(session.gesture.active)
        session.move(Offset(100f, 170f), 8f, bounds)
        assertEquals(1, session.gesture.selected)
        assertEquals(ContextMenuRelease.Select(1), session.release(Offset(100f, 170f), bounds))
    }

    @Test fun stationaryLongPressReleaseKeepsMenuOpenForLaterTap() {
        val session = longPress()
        assertEquals(ContextMenuRelease.KeepOpen, session.release(Offset(100f, 250f), bounds))
        assertFalse(session.gesture.active)
        assertTrue(session.menuOpen)
    }

    @Test fun smallJitterDoesNotDismissLongPressMenu() {
        val session = longPress()
        session.move(Offset(102f, 251f), 8f, bounds)
        assertEquals(ContextMenuRelease.KeepOpen, session.release(Offset(102f, 251f), bounds))
    }

    @Test fun sourceDragOutsideThenReleaseKeepsMenuWithoutChoosingOldSelection() {
        val session = longPress()
        session.move(Offset(100f, 170f), 8f, bounds)
        session.move(Offset(220f, 170f), 8f, bounds)
        assertEquals(ContextMenuRelease.KeepOpen, session.release(Offset(220f, 170f), bounds))
        assertNull(session.selection.selected)
        assertTrue(session.menuOpen)
    }

    @Test fun independentMenuRowDragOutsideStillDismissesWithoutExecuting() {
        val session = ContextMenuGestureSession()
        session.open()
        session.ready = true
        assertTrue(session.gesture.begin(Offset(100f, 124f), bounds))
        session.move(Offset(300f, 350f), 8f, bounds)
        assertEquals(ContextMenuRelease.Dismiss, session.release(Offset(300f, 350f), bounds))
        assertNull(session.selection.selected)
    }

    @Test fun sourceDragCanBeRearmedAfterReleaseAndStillSelectExactlyOnce() {
        val session = longPress()
        session.move(Offset(500f, 400f), 8f, bounds)
        assertEquals(ContextMenuRelease.KeepOpen, session.release(Offset(500f, 400f), bounds))
        session.continuation = true
        session.gesture.arm()
        session.move(Offset(100f, 170f), 8f, bounds)
        assertEquals(ContextMenuRelease.Select(1), session.release(Offset(100f, 170f), bounds))
        assertFalse(session.release(Offset(100f, 170f), bounds) is ContextMenuRelease.Select)
    }

    @Test fun releaseBeforeMeasurementDoesNotRunUnmeasuredAction() {
        val session = longPress()
        session.ready = false
        session.move(Offset(100f, 170f), 8f, bounds)
        assertEquals(ContextMenuRelease.KeepOpen, session.release(Offset(100f, 170f), bounds))
    }

    @Test fun keyboardOpeningDoesNotArmAPhantomTouch() {
        val session = ContextMenuGestureSession()
        session.open()
        assertFalse(session.continuation)
        assertFalse(session.gesture.active)
        assertNull(session.feedbackPoint)
    }

    @Test fun closeCancelsSelectionAndPanelMotion() {
        val session = longPress()
        session.move(Offset(100f, 170f), 8f, bounds)
        assertNotNull(session.feedbackPoint)
        session.close()
        assertFalse(session.menuOpen)
        assertFalse(session.ready)
        assertFalse(session.gesture.active)
        assertNull(session.feedbackPoint)
        assertFalse(session.release(Offset(100f, 170f), bounds) is ContextMenuRelease.Select)
    }

    @Test fun aCompletedGestureCannotSelectAnActionTwice() {
        val session = longPress()
        session.move(Offset(100f, 124f), 8f, bounds)
        assertEquals(ContextMenuRelease.Select(0), session.release(Offset(100f, 124f), bounds))
        assertFalse(session.release(Offset(100f, 124f), bounds) is ContextMenuRelease.Select)
        assertNull(session.selection.selected)
        assertNull(session.feedbackPoint)
    }
}
