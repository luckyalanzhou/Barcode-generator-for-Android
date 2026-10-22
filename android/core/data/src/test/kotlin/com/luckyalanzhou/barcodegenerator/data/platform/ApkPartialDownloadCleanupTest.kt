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

    private fun File.writeFile(name: String): File = File(this, name).apply { writeText("test") }
}
