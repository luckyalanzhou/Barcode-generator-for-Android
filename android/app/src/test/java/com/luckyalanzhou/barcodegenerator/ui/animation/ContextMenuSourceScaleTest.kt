package com.luckyalanzhou.barcodegenerator.ui.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMenuSourceScaleTest {
    @Test fun sourceUsesSubtleFocusRatherThanFixedLargeEnlargement() {
        assertEquals(1f, contextMenuSourceScale(0f, 0f, false), 0f)
        assertEquals(1.04f, contextMenuSourceScale(1f, 0f, false), .00001f)
        assertEquals(1.02f, contextMenuSourceScale(.5f, 0f, false), .00001f)
    }
    @Test fun cancellationAndActionRestoreOriginalSize() {
        assertEquals(1f, contextMenuSourceScale(0f, 0f, false), 0f)
        assertEquals(1f, contextMenuSourceScale(1f, 1f, false), 0f)
        assertTrue(contextMenuSourceScale(1f, .5f, false) < 1.04f)
    }
    @Test fun reducedMotionAndInvalidInputsNeverEnlarge() {
        assertEquals(1f, contextMenuSourceScale(1f, 0f, true), 0f)
        assertEquals(1f, contextMenuSourceScale(Float.NaN, 0f, false), 0f)
        assertEquals(1.04f, contextMenuSourceScale(2f, -1f, false), .00001f)
    }

    @Test fun focusAdaptsToTargetSizeAndBoundsInvalidValues() {
        assertEquals(1.0625f, contextMenuFocusScale(64f, 54f, 2f), .00001f)
        assertEquals(1.01f, contextMenuFocusScale(400f, 48f, 2f), .00001f)
        assertEquals(1.08f, contextMenuFocusScale(10f, 10f, 2f), .00001f)
        assertEquals(1f, contextMenuFocusScale(0f, 0f, 2f), 0f)
        assertEquals(1f, contextMenuFocusScale(Float.NaN, 40f, 2f), 0f)
        assertEquals(1f, contextMenuSourceScale(1f, 0f, false, Float.NaN), 0f)
        assertEquals(1.08f, contextMenuSourceScale(1f, 0f, false, 2f), .00001f)
    }
}
