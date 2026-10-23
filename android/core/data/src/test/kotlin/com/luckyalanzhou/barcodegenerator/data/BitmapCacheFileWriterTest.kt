package com.luckyalanzhou.barcodegenerator.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BitmapCacheFileWriterTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("bitmap-cache-writer-test").toFile()
    }

    @After
    fun tearDown() {
        directory.listFiles().orEmpty().forEach(File::delete)
        directory.delete()
    }

    @Test
    fun compressionExceptionBecomesCacheMissAndDeletesPartialFile() {
        val temporary = File(directory, ".partial.tmp")

        val completed = writeBitmapCacheFile(temporary) { output ->
            output.write(byteArrayOf(1, 2, 3))
            throw IOException("cache disk write failed")
        }

        assertFalse(completed)
        assertFalse(temporary.exists())
    }

    @Test
    fun compressionErrorPropagatesAndDeletesPartialFile() {
        val temporary = File(directory, ".partial.tmp")
        val fatal = OutOfMemoryError("bitmap compression exhausted memory")
        var observed: OutOfMemoryError? = null

        try {
            writeBitmapCacheFile(temporary) { output ->
                output.write(byteArrayOf(1, 2, 3))
                throw fatal
            }
        } catch (error: OutOfMemoryError) {
            observed = error
        }

        assertSame(fatal, observed)
        assertFalse(temporary.exists())
    }

    @Test
    fun successfulCompressionKeepsTemporaryFileForAtomicCommit() {
        val temporary = File(directory, ".complete.tmp")

        val completed = writeBitmapCacheFile(temporary) { output ->
            output.write(byteArrayOf(1, 2, 3))
            true
        }

        assertTrue(completed)
        assertTrue(temporary.isFile)
    }
}
