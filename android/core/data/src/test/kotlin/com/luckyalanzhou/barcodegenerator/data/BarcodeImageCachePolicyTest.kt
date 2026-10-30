package com.luckyalanzhou.barcodegenerator.data

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BarcodeImageCachePolicyTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("barcode-image-cache-test").toFile()
    }

    @After
    fun tearDown() {
        directory.listFiles().orEmpty().forEach(File::delete)
        directory.delete()
    }

    @Test
    fun pruneEvictsOldestImagesUntilByteAndFileBoundsAreMet() {
        val oldest = image("oldest.png", 4, modified = 1L)
        val middle = image("middle.png", 4, modified = 2L)
        val newest = image("newest.png", 4, modified = 3L)

        BarcodeImageCachePolicy.prune(directory, newest, maxBytes = 8, maxImageFiles = 2)

        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
    }

    @Test
    fun pruneDoesNotDeleteAnotherActiveWritersTemporaryImage() {
        val current = image("current.png", 1, modified = 2L)
        val active = File(directory, ".active.tmp").apply { writeText("still writing") }
        val stale = File(directory, ".stale.tmp").apply { writeText("abandoned") }
        BarcodeImageCachePolicy.prune(directory, current, activeTemporaryNames = setOf(active.name))
        assertTrue(active.exists())
        assertFalse(stale.exists())
    }

    @Test
    fun maintenanceAvoidsRepeatedScansButKeepsBudgetBounded() {
        val maintenance = BarcodeCacheMaintenance(maxBytes = 8, maxFiles = 2)
        val first = image("first.png", 3, modified = 1L)
        maintenance.onStored(directory, first, emptySet())
        val second = image("second.png", 3, modified = 2L)
        maintenance.onStored(directory, second, emptySet())
        assertTrue(maintenance.directoryScans == 1)
        val third = image("third.png", 3, modified = 3L)
        maintenance.onStored(directory, third, emptySet())
        assertTrue(maintenance.directoryScans == 2)
        assertFalse(first.exists())
        assertTrue(second.exists())
        assertTrue(third.exists())
    }

    @Test
    fun pruneKeepsRecentlyUsedImageAndRemovesStaleTemporaryFiles() {
        val previous = image("previous.png", 1, modified = 1L)
        val current = image("current.png", 1, modified = 2L)
        val temporary = File(directory, ".unfinished.tmp").apply { writeText("partial") }

        BarcodeImageCachePolicy.prune(directory, current)

        assertTrue(previous.exists())
        assertTrue(current.exists())
        assertFalse(temporary.exists())
    }

    @Test
    fun fatalProtectedPathResolutionIsPropagatedBeforeAnyImageCanBeEvicted() {
        val current = image("current.png", 4, modified = 1L)
        val fatal = AssertionError("canonical path resolution failed")
        val protectedFile = object : File(current.absolutePath) {
            override fun getCanonicalFile(): File = throw fatal
        }

        val observed = try {
            BarcodeImageCachePolicy.prune(directory, protectedFile, maxBytes = 0, maxImageFiles = 0)
            null
        } catch (error: AssertionError) {
            error
        }

        assertSame(fatal, observed)
        assertTrue(current.exists())
    }

    @Test
    fun pruneDropsNewImageWhenOldEntriesCannotBeEvictedWithinBudget() {
        val undeletableOld = object : File(directory, "locked.png") {
            override fun isFile() = true
            override fun length() = 7L
            override fun lastModified() = 1L
            override fun delete() = false
        }
        val current = image("current.png", 3, modified = 2L)
        val cacheDirectory = object : File(directory.path) {
            override fun listFiles(): Array<File> = arrayOf(undeletableOld, current)
        }

        BarcodeImageCachePolicy.prune(cacheDirectory, current, maxBytes = 8, maxImageFiles = 10)

        assertFalse(current.exists())
        assertTrue(undeletableOld.isFile)
    }

    @Test
    fun maintenanceDropsNewImageWhenOldEntryDeletionThrows() {
        val undeletableOld = object : File(directory, "locked.png") {
            override fun isFile() = true
            override fun length() = 7L
            override fun lastModified() = 1L
            override fun delete(): Boolean = throw SecurityException("cache eviction denied")
        }
        val current = image("current.png", 3, modified = 2L)
        val cacheDirectory = object : File(directory.path) {
            override fun listFiles(): Array<File> = arrayOf(undeletableOld, current)
        }

        runRegenerableCacheWrite {
            BarcodeCacheMaintenance(maxBytes = 8, maxFiles = 10)
                .onStored(cacheDirectory, current, emptySet())
        }

        assertFalse(current.exists())
        assertTrue(undeletableOld.isFile)
    }

    @Test
    fun maintenanceContinuesImagePruningWhenStaleTemporaryDeletionThrows() {
        val old = object : File(directory, "old.png") {
            override fun isFile() = true
            override fun length() = 7L
            override fun lastModified() = 1L
            override fun delete() = false
        }
        val current = image("current.png", 3, modified = 2L)
        val undeletableTemporary = object : File(directory, ".stale.tmp") {
            override fun isFile() = true
            override fun delete(): Boolean = throw SecurityException("temporary cleanup denied")
        }
        val cacheDirectory = object : File(directory.path) {
            override fun listFiles(): Array<File> = arrayOf(old, current, undeletableTemporary)
        }

        runRegenerableCacheWrite {
            BarcodeCacheMaintenance(maxBytes = 8, maxFiles = 10)
                .onStored(cacheDirectory, current, emptySet())
        }

        assertFalse(current.exists())
        assertTrue(old.isFile)
    }

    @Test
    fun maintenanceDoesNotTreatFailedDirectoryListingAsAnEmptyCache() {
        val old = image("old.png", 7, modified = 1L)
        val first = image("first.png", 3, modified = 2L)
        val cacheDirectory = object : File(directory.path) {
            override fun listFiles(): Array<File>? = null
        }
        val maintenance = BarcodeCacheMaintenance(maxBytes = 8, maxFiles = 10)

        runRegenerableCacheWrite { maintenance.onStored(cacheDirectory, first, emptySet()) }

        val second = image("second.png", 3, modified = 3L)
        runRegenerableCacheWrite { maintenance.onStored(cacheDirectory, second, emptySet()) }

        assertFalse(first.exists())
        assertFalse(second.exists())
        assertTrue(old.exists())
    }

    private fun image(name: String, size: Int, modified: Long): File = File(directory, name).apply {
        writeBytes(ByteArray(size))
        setLastModified(modified)
    }
}
