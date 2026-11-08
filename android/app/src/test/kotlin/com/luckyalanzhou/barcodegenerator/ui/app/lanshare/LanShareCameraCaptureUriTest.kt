package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanShareCameraCaptureUriTest {
    @Test
    fun nonEmptyExplicitOutputSkipsRecentMediaLookup() {
        val output = "camera-output"

        val result = resolveLanCameraCaptureTarget(output, outputHasContent = true, cameraResultUri = "camera-result")

        assertEquals(output, result)
    }

    @Test
    fun emptyExplicitOutputUsesUriReturnedByCamera() {
        val result = resolveLanCameraCaptureTarget(
            "camera-output",
            outputHasContent = false,
            cameraResultUri = "camera-result",
        )

        assertEquals("camera-result", result)
    }

    @Test
    fun emptyExplicitOutputWithoutReturnedUriHasNoImplicitGalleryTarget() {
        val result = resolveLanCameraCaptureTarget(
            "camera-output",
            outputHasContent = false,
            cameraResultUri = null,
        )

        assertNull(result)
    }

    @Test
    fun missingExplicitOutputWithoutReturnedUriHasNoImplicitGalleryTarget() {
        val result = resolveLanCameraCaptureTarget(
            null,
            outputHasContent = false,
            cameraResultUri = null,
        )

        assertNull(result)
    }
}
