package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InterruptedCameraCaptureCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun removesOnlyOldUnprotectedCameraCaptureFiles() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val cutoff = System.currentTimeMillis() - 60_000L
        val interruptedLanCapture = oldFile(cacheDirectory, "lan_share_photo_123.jpg", cutoff)
        val interruptedOcrCapture = oldFile(cacheDirectory, "barcode_camera_456.jpg", cutoff)
        val activeCapture = File(cacheDirectory, "lan_share_photo_active.jpg").apply { writeText("active") }
        val protectedCapture = oldFile(cacheDirectory, "barcode_camera_pending.jpg", cutoff)
        val unrelatedFile = oldFile(cacheDirectory, "user-photo.jpg", cutoff)
        val unrelatedDirectory = File(cacheDirectory, "barcode_camera_folder.jpg").apply { mkdir() }

        assertEquals(
            2,
            cleanupInterruptedCameraCaptureFiles(
                cacheDirectory = cacheDirectory,
                modifiedBeforeMillis = cutoff,
                protectedPaths = setOf(protectedCapture.absolutePath),
            ),
        )

        assertFalse(interruptedLanCapture.exists())
        assertFalse(interruptedOcrCapture.exists())
        assertTrue(activeCapture.exists())
        assertTrue(protectedCapture.exists())
        assertTrue(unrelatedFile.exists())
        assertTrue(unrelatedDirectory.exists())
    }

    @Test
    fun shareableCameraFilesUseTheirDedicatedProviderSubtree() {
        val cacheDirectory = temporaryFolder.newFolder("shareable-camera-cache")
        val cameraDirectory = ensureShareableCacheDirectory(cacheDirectory, SHAREABLE_CAMERA_DIRECTORY)
        val cutoff = System.currentTimeMillis() - 60_000L
        val staleCapture = oldFile(cameraDirectory, "barcode_camera_stale.jpg", cutoff)
        val unrelatedRootFile = oldFile(cacheDirectory, "barcode_camera_unrelated.jpg", cutoff)

        assertEquals(
            1,
            cleanupInterruptedCameraCaptureFiles(
                cacheDirectory = cameraDirectory,
                modifiedBeforeMillis = cutoff,
                protectedPaths = emptySet(),
            ),
        )

        assertFalse(staleCapture.exists())
        assertTrue(unrelatedRootFile.exists())
        assertEquals(File(cacheDirectory, "share/camera").canonicalFile, cameraDirectory.canonicalFile)
    }

    private fun oldFile(directory: File, name: String, modifiedAt: Long): File =
        File(directory, name).apply {
            writeText("temporary")
            setLastModified(modifiedAt - 1_000L)
        }
}
