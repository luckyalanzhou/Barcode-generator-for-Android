package com.luckyalanzhou.barcodegenerator.data.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkPartialDownloadCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun removesOnlyOrphanedApkPartialFiles() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val orphanedPartial = cacheDir.writeFile("barcode-generator-update-old.apk.part")
        val activePartial = cacheDir.writeFile("barcode-generator-update-active.apk.part")
        val completedApk = cacheDir.writeFile("barcode-generator-update-ready.apk")
        val unrelatedPartial = cacheDir.writeFile("other-download.apk.part")
        val similarButUnowned = cacheDir.writeFile("barcode-generator-update-extra.apk.partial")

        val removedCount = cleanupOrphanedApkPartFiles(cacheDir, setOf(activePartial))

        assertEquals(1, removedCount)
        assertFalse(orphanedPartial.exists())
        assertTrue(activePartial.exists())
        assertTrue(completedApk.exists())
        assertTrue(unrelatedPartial.exists())
        assertTrue(similarButUnowned.exists())
    }

    @Test
    fun removesOnlyExpiredCompletedUpdatePackages() {
        val cacheDir = temporaryFolder.newFolder("completed-cache")
        val nowMillis = System.currentTimeMillis()
        val expiredBeforeMillis = nowMillis - UPDATE_APK_CACHE_RETENTION_MILLIS
        val expiredApk = cacheDir.writeFile("barcode-generator-update-old.apk", expiredBeforeMillis - 1L)
        val pendingApk = cacheDir.writeFile("barcode-generator-update-pending.apk", expiredBeforeMillis - 1L)
        val sharedDirectory = File(cacheDir, UPDATE_APK_SHARE_CACHE_DIRECTORY).apply { mkdirs() }
        val expiredSharedApk = sharedDirectory.writeFile("barcode-generator-update-shared-old.apk", expiredBeforeMillis - 1L)
        val pendingSharedApk = sharedDirectory.writeFile("barcode-generator-update-shared-pending.apk", expiredBeforeMillis - 1L)
        val boundaryApk = cacheDir.writeFile("barcode-generator-update-boundary.apk", expiredBeforeMillis)
        val recentApk = cacheDir.writeFile("barcode-generator-update-recent.apk", expiredBeforeMillis + 1L)
        val unrelatedApk = cacheDir.writeFile("user-export.apk", expiredBeforeMillis - 1L)
        val partialDownload = cacheDir.writeFile("barcode-generator-update-active.apk.part", expiredBeforeMillis - 1L)
        val apkNamedDirectory = File(cacheDir, "barcode-generator-update-directory.apk").apply { mkdir() }

        val removedCount = cleanupExpiredApkDownloadFiles(
            cacheDir,
            protectedApkPaths = setOf(pendingApk.absolutePath, pendingSharedApk.absolutePath),
            nowMillis = nowMillis,
        )

        assertEquals(2, removedCount)
        assertFalse(expiredApk.exists())
        assertFalse(expiredSharedApk.exists())
        assertTrue(pendingApk.exists())
        assertTrue(pendingSharedApk.exists())
        assertTrue(boundaryApk.exists())
        assertTrue(recentApk.exists())
        assertTrue(unrelatedApk.exists())
        assertTrue(partialDownload.exists())
        assertTrue(apkNamedDirectory.exists())
    }

    private fun File.writeFile(name: String, modifiedAt: Long? = null): File = File(this, name).apply {
        writeText("test")
        modifiedAt?.let { assertTrue(setLastModified(it)) }
    }
}
