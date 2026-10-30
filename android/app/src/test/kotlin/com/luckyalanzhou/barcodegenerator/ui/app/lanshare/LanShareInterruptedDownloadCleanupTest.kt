package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanShareInterruptedDownloadCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun removesOnlyLanShareDownloadTemporaryFilesFromCacheRoot() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val cutoff = System.currentTimeMillis() - 60_000L
        val outerTemporary = File(cacheDirectory, "lan-download-ab12.part").apply {
            writeText("partial")
            setLastModified(cutoff - 1_000L)
        }
        val innerTemporary = File(cacheDirectory, ".lan-download-ab12.part.347.part").apply {
            writeText("partial")
            setLastModified(cutoff - 1_000L)
        }
        val activeTemporary = File(cacheDirectory, "lan-download-active.part").apply { writeText("active") }
        val unrelatedTemporary = File(cacheDirectory, "other.part").apply { writeText("keep") }
        val updateTemporary = File(cacheDirectory, "update-1.apk.part").apply { writeText("keep") }
        val completedFile = File(cacheDirectory, "lan-download-complete.apk").apply { writeText("keep") }
        val previewDirectory = File(cacheDirectory, "lan-share-preview").apply { mkdir() }
        val previewFile = File(previewDirectory, "preview.jpg").apply { writeText("keep") }

        assertEquals(2, cleanupInterruptedLanShareDownloads(cacheDirectory, cutoff))

        assertFalse(outerTemporary.exists())
        assertFalse(innerTemporary.exists())
        assertTrue(activeTemporary.exists())
        assertTrue(unrelatedTemporary.exists())
        assertTrue(updateTemporary.exists())
        assertTrue(completedFile.exists())
        assertTrue(previewFile.exists())
    }

    @Test
    fun removesOnlyIncompleteLanShareUploadsFromPersistentSessionDirectory() {
        val sharedDirectory = temporaryFolder.newFolder("lan-share")
        val cutoff = System.currentTimeMillis() - 60_000L
        val interruptedUpload = File(sharedDirectory, ".lan-upload-ab12.part").apply {
            writeText("partial")
            setLastModified(cutoff - 1_000L)
        }
        val activeUpload = File(sharedDirectory, ".lan-upload-live.part").apply { writeText("active") }
        val completedUpload = File(sharedDirectory, "app_12345_photo.jpg").apply { writeText("keep") }
        val unrelatedTemporary = File(sharedDirectory, ".other-upload.part").apply { writeText("keep") }

        assertEquals(1, cleanupInterruptedLanShareUploads(sharedDirectory, cutoff))

        assertFalse(interruptedUpload.exists())
        assertTrue(activeUpload.exists())
        assertTrue(completedUpload.exists())
        assertTrue(unrelatedTemporary.exists())
    }

    @Test
    fun removesOnlyInterruptedPreviewCacheFilesFromLanShareManagerCacheSubdirectory() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val previewCacheDirectory = File(
            cacheDirectory,
            LanShareManager.WEB_PREVIEW_CACHE_DIRECTORY_NAME,
        ).apply { mkdir() }
        val cutoff = System.currentTimeMillis() - 60_000L
        val interruptedPreview = File(previewCacheDirectory, ".${"a".repeat(64)}.347.part").apply {
            writeText("partial")
            setLastModified(cutoff - 1_000L)
        }
        val activePreview = File(previewCacheDirectory, ".${"b".repeat(64)}.348.part").apply {
            writeText("active")
        }
        val unrelatedTemporary = File(previewCacheDirectory, ".other-upload.part").apply { writeText("keep") }
        val completedPreview = File(previewCacheDirectory, "${"c".repeat(64)}.preview.jpg").apply {
            writeText("keep")
        }

        assertEquals(
            1,
            cleanupInterruptedLanSharePreviews(
                cacheDirectory,
                cutoff,
                LanShareManager.WEB_PREVIEW_CACHE_DIRECTORY_NAME,
            ),
        )

        assertFalse(interruptedPreview.exists())
        assertTrue(activePreview.exists())
        assertTrue(unrelatedTemporary.exists())
        assertTrue(completedPreview.exists())
    }
}
