package com.luckyalanzhou.barcodegenerator.data

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertFalse
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
    fun pruneKeepsRecentlyUsedImageAndRemovesStaleTemporaryFiles() {
        val previous = image("previous.png", 1, modified = 1L)
        val current = image("current.png", 1, modified = 2L)
        val temporary = File(directory, ".unfinished.tmp").apply { writeText("partial") }

        BarcodeImageCachePolicy.prune(directory, current)

        assertTrue(previous.exists())
        assertTrue(current.exists())
        assertFalse(temporary.exists())
    }

    private fun image(name: String, size: Int, modified: Long): File = File(directory, name).apply {
        writeBytes(ByteArray(size))
        setLastModified(modified)
    }
}
