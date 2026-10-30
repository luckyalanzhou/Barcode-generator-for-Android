package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareCameraCaptureUriTest {
    @Test
    fun nonEmptyExplicitOutputSkipsRecentMediaLookup() {
        var fallbackCalled = false
        val output = "camera-output"

        val result = resolveLanCameraCaptureTarget(output, outputHasContent = true) {
            fallbackCalled = true
            "recent-media"
        }

        assertEquals(output, result)
        assertFalse(fallbackCalled)
    }

    @Test
    fun emptyExplicitOutputUsesRecentMediaLookup() {
        val recent = "recent-media"
        var fallbackCalled = false

        val result = resolveLanCameraCaptureTarget(
            "camera-output",
            outputHasContent = false,
        ) {
            fallbackCalled = true
            recent
        }

        assertEquals(recent, result)
        assertTrue(fallbackCalled)
    }

    @Test
    fun missingExplicitOutputUsesRecentMediaLookup() {
        val recent = "recent-media"

        val result = resolveLanCameraCaptureTarget(null, outputHasContent = false) { recent }

        assertEquals(recent, result)
    }
}
