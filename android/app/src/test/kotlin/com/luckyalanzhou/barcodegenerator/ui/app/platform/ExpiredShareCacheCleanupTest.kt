package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExpiredShareCacheCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun removesOnlyExpiredVariantBackupZipsAndSharedPngFiles() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val nowMillis = System.currentTimeMillis()
        val cutoff = nowMillis - 7L * 24L * 60L * 60L * 1_000L
        val backupFileName = "barcode-generator-backup-beta.zip"
        val oldBackup = oldFile(
            cacheDirectory,
            "barcode-generator-backup-beta-20261016-101112.zip",
            cutoff - 1L,
        )
        val boundaryBackup = timestampedFile(
            cacheDirectory,
            "barcode-generator-backup-beta-20261016-101113.zip",
            cutoff,
        )
        val recentBackup = timestampedFile(
            cacheDirectory,
            "barcode-generator-backup-beta-20261016-101114.zip",
            cutoff + 1L,
        )
        val otherVariantBackup = oldFile(
            cacheDirectory,
            "barcode-generator-backup-official-20261016-101112.zip",
            cutoff - 1L,
        )
        val unrelatedZip = oldFile(cacheDirectory, "user-export.zip", cutoff - 1L)

        val imageDirectory = File(cacheDirectory, "shared-images").apply { mkdir() }
        val oldImage = oldFile(imageDirectory, "result-image-old.png", cutoff - 1L)
        val boundaryImage = timestampedFile(imageDirectory, "result-image-boundary.png", cutoff)
        val recentImage = timestampedFile(imageDirectory, "result-image-recent.png", cutoff + 1L)
        val unrelatedImage = oldFile(imageDirectory, "unrelated.jpg", cutoff - 1L)
        val nestedDirectory = File(imageDirectory, "nested.png").apply { mkdir() }
        val nestedImage = oldFile(nestedDirectory, "nested.png", cutoff - 1L)

        assertEquals(
            2,
            cleanupExpiredShareCacheArtifacts(cacheDirectory, backupFileName, nowMillis),
        )

        assertFalse(oldBackup.exists())
        assertFalse(oldImage.exists())
        assertTrue(boundaryBackup.exists())
        assertTrue(recentBackup.exists())
        assertTrue(otherVariantBackup.exists())
        assertTrue(unrelatedZip.exists())
        assertTrue(boundaryImage.exists())
        assertTrue(recentImage.exists())
        assertTrue(unrelatedImage.exists())
        assertTrue(nestedDirectory.exists())
        assertTrue(nestedImage.exists())
    }

    @Test
    fun missingCacheDirectoryIsAnEmptyCleanup() {
        val missingDirectory = File(temporaryFolder.root, "missing-cache")

        assertEquals(
            0,
            cleanupExpiredShareCacheArtifacts(
                missingDirectory,
                "barcode-generator-backup-beta.zip",
                System.currentTimeMillis(),
            ),
        )
        assertFalse(missingDirectory.exists())
    }

    @Test
    fun removesExpiredProviderScopedBackupAndImageArtifacts() {
        val cacheDirectory = temporaryFolder.newFolder("shared-cache")
        val nowMillis = System.currentTimeMillis()
        val cutoff = nowMillis - SHARE_CACHE_ARTIFACT_RETENTION_MILLIS
        val favoritesDirectory = File(cacheDirectory, "$SHAREABLE_CACHE_ROOT/$SHAREABLE_FAVORITES_DIRECTORY")
            .apply { mkdirs() }
        val imagesDirectory = File(cacheDirectory, "$SHAREABLE_CACHE_ROOT/$SHAREABLE_IMAGES_DIRECTORY")
            .apply { mkdirs() }
        val oldBackup = oldFile(favoritesDirectory, "barcode-generator-backup-beta-old.zip", cutoff - 1L)
        val recentBackup = timestampedFile(favoritesDirectory, "barcode-generator-backup-beta-recent.zip", cutoff + 1L)
        val oldImage = oldFile(imagesDirectory, "result-image-old.png", cutoff - 1L)
        val unrelated = oldFile(imagesDirectory, "unrelated.jpg", cutoff - 1L)

        assertEquals(
            2,
            cleanupExpiredShareCacheArtifacts(cacheDirectory, "barcode-generator-backup-beta.zip", nowMillis),
        )

        assertFalse(oldBackup.exists())
        assertFalse(oldImage.exists())
        assertTrue(recentBackup.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun removesOnlyInterruptedFavoriteExportTemporaryZipsAfterStartupGrace() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val nowMillis = System.currentTimeMillis()
        val graceMillis = INTERRUPTED_FAVORITE_EXPORT_GRACE_MILLIS
        val staleTemporaryZip = oldFile(
            cacheDirectory,
            "favorites-export-123456789.zip",
            nowMillis - graceMillis - 1L,
        )
        val boundaryTemporaryZip = timestampedFile(
            cacheDirectory,
            "favorites-export-boundary.zip",
            nowMillis - graceMillis,
        )
        val recentTemporaryZip = timestampedFile(
            cacheDirectory,
            "favorites-export-recent.zip",
            nowMillis - graceMillis + 1L,
        )
        val unrelatedZip = oldFile(cacheDirectory, "user-export.zip", nowMillis - 60_000L)

        assertEquals(
            1,
            cleanupExpiredShareCacheArtifacts(cacheDirectory, "barcode-generator-backup-beta.zip", nowMillis),
        )

        assertFalse(staleTemporaryZip.exists())
        assertTrue(boundaryTemporaryZip.exists())
        assertTrue(recentTemporaryZip.exists())
        assertTrue(unrelatedZip.exists())
    }

    @Test
    fun removesOnlyExpiredResultDocumentPngsFromTheCacheRoot() {
        val cacheDirectory = temporaryFolder.newFolder("result-cache")
        val nowMillis = System.currentTimeMillis()
        val cutoff = nowMillis - SHARE_CACHE_ARTIFACT_RETENTION_MILLIS
        val expiredResult = oldFile(cacheDirectory, "result-export-123456789.png", cutoff - 1L)
        val boundaryResult = timestampedFile(cacheDirectory, "result-export-boundary.png", cutoff)
        val recentResult = timestampedFile(cacheDirectory, "result-export-recent.png", cutoff + 1L)
        val unrelatedPng = oldFile(cacheDirectory, "user-photo.png", cutoff - 1L)
        val similarPrefix = oldFile(cacheDirectory, "old-result-export.png", cutoff - 1L)
        val wrongExtension = oldFile(cacheDirectory, "result-export-old.jpg", cutoff - 1L)
        val nestedDirectory = File(cacheDirectory, "result-export-nested.png").apply { mkdir() }
        val nestedResult = oldFile(nestedDirectory, "result-export-nested.png", cutoff - 1L)

        assertEquals(
            1,
            cleanupExpiredShareCacheArtifacts(cacheDirectory, "barcode-generator-backup-beta.zip", nowMillis),
        )

        assertFalse(expiredResult.exists())
        assertTrue(boundaryResult.exists())
        assertTrue(recentResult.exists())
        assertTrue(unrelatedPng.exists())
        assertTrue(similarPrefix.exists())
        assertTrue(wrongExtension.exists())
        assertTrue(nestedDirectory.exists())
        assertTrue(nestedResult.exists())
    }

    private fun oldFile(directory: File, name: String, modifiedAt: Long): File =
        timestampedFile(directory, name, modifiedAt)

    private fun timestampedFile(directory: File, name: String, modifiedAt: Long): File =
        File(directory, name).apply {
            writeText("cache")
            assertTrue(setLastModified(modifiedAt))
        }
}
