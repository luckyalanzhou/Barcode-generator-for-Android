package com.luckyalanzhou.barcodegenerator.ui.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMenuSourceScaleTest {
    @Test fun completeSourceSettlesAtExactlyOnePointFifteen() {
        assertEquals(1f, contextMenuSourceScale(0f, 0f, false), 0f)
        assertEquals(1.15f, contextMenuSourceScale(1f, 0f, false), .00001f)
        assertEquals(1.075f, contextMenuSourceScale(.5f, 0f, false), .00001f)
    }
    @Test fun cancellationAndActionRestoreOriginalSize() {
        assertEquals(1f, contextMenuSourceScale(0f, 0f, false), 0f)
        assertEquals(1f, contextMenuSourceScale(1f, 1f, false), 0f)
        assertTrue(contextMenuSourceScale(1f, .5f, false) < 1.15f)
    }
    @Test fun reducedMotionAndInvalidInputsNeverEnlarge() {
        assertEquals(1f, contextMenuSourceScale(1f, 0f, true), 0f)
        assertEquals(1f, contextMenuSourceScale(Float.NaN, 0f, false), 0f)
        assertEquals(1.15f, contextMenuSourceScale(2f, -1f, false), .00001f)
    }
}
