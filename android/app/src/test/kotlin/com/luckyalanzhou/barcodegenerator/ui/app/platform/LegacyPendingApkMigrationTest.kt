package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyPendingApkMigrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun movesLegacyPendingUpdateApkIntoMappedCacheWithoutChangingContents() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val legacyApk = File(cacheDirectory, "barcode-generator-update-old.apk").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val shareableApk = moveLegacyPendingApkToShareableCache(cacheDirectory, legacyApk)

        assertFalse(legacyApk.exists())
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), shareableApk.readBytes().toList())
        assertEquals(File(cacheDirectory, "share/updates").canonicalFile, shareableApk.canonicalFile.parentFile)
    }

    @Test
    fun leavesAlreadyShareableAndUnrelatedFilesUnchanged() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val updatesDirectory = File(cacheDirectory, "share/updates").apply { mkdirs() }
        val currentApk = File(updatesDirectory, "barcode-generator-update-current.apk").apply {
            writeText("current")
        }
        val unrelatedFile = File(cacheDirectory, "unrelated.apk").apply { writeText("unrelated") }

        assertSame(currentApk, moveLegacyPendingApkToShareableCache(cacheDirectory, currentApk))
        assertSame(unrelatedFile, moveLegacyPendingApkToShareableCache(cacheDirectory, unrelatedFile))
        assertEquals("unrelated", unrelatedFile.readText())
    }

    @Test
    fun keepsLegacyApkWhenShareableDirectoryCannotBeCreated() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val legacyApk = File(cacheDirectory, "barcode-generator-update-old.apk").apply {
            writeText("keep")
        }
        File(cacheDirectory, "share").writeText("blocks directory creation")

        runCatching { moveLegacyPendingApkToShareableCache(cacheDirectory, legacyApk) }
            .onSuccess { error("Expected migration to fail when the share directory cannot be created") }

        assertEquals("keep", legacyApk.readText())
    }
}
