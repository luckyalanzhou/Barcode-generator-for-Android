package com.luckyalanzhou.barcodegenerator.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
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
    fun cleanupExceptionDoesNotEscapeBestEffortCacheWrite() {
        val path = File(directory, ".cleanup-fails.tmp")
        var cleanupFailuresRemaining = 1
        val temporary = object : File(path.absolutePath) {
            override fun delete(): Boolean {
                if (cleanupFailuresRemaining > 0) {
                    cleanupFailuresRemaining--
                    throw SecurityException("cache cleanup denied")
                }
                return super.delete()
            }
        }

        val completed = writeBitmapCacheFile(temporary) { output ->
            output.write(byteArrayOf(1, 2, 3))
            throw IOException("cache disk write failed")
        }

        assertFalse(completed)
        assertTrue("failed cleanup may leave only a hidden cache temporary", temporary.isFile)
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
    fun fatalCompressionErrorRemainsPrimaryWhenCleanupAlsoFailsFatally() {
        val path = File(directory, ".cleanup-fatal.tmp")
        val compressionFailure = OutOfMemoryError("bitmap compression exhausted memory")
        val cleanupFailure = AssertionError("cache temporary cleanup failed")
        val temporary = object : File(path.absolutePath) {
            override fun delete(): Boolean = throw cleanupFailure
        }

        val observed = try {
            writeBitmapCacheFile(temporary) { output ->
                output.write(byteArrayOf(1, 2, 3))
                throw compressionFailure
            }
            null
        } catch (error: Error) {
            error
        }

        assertSame(compressionFailure, observed)
        assertSame(cleanupFailure, compressionFailure.suppressed.single())
    }

    @Test
    fun fatalCleanupErrorTakesPriorityOverOrdinaryCompressionFailure() {
        val path = File(directory, ".cleanup-fatal.tmp")
        val compressionFailure = IOException("cache disk write failed")
        val cleanupFailure = AssertionError("cache temporary cleanup failed")
        val temporary = object : File(path.absolutePath) {
            override fun delete(): Boolean = throw cleanupFailure
        }

        val observed = try {
            writeBitmapCacheFile(temporary) { output ->
                output.write(byteArrayOf(1, 2, 3))
                throw compressionFailure
            }
            null
        } catch (error: Error) {
            error
        }

        assertSame(cleanupFailure, observed)
        assertSame(compressionFailure, cleanupFailure.suppressed.single())
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

    @Test
    fun ordinaryDiskCacheMaintenanceFailureDoesNotEscape() {
        runRegenerableCacheWrite { throw SecurityException("cache maintenance denied") }
    }

    @Test
    fun diskCacheMaintenanceCancellationPropagates() {
        val cancellation = CancellationException("cache maintenance cancelled")
        var observed: CancellationException? = null

        try {
            runRegenerableCacheWrite { throw cancellation }
        } catch (error: CancellationException) {
            observed = error
        }

        assertSame(cancellation, observed)
    }

    @Test
    fun diskCacheMaintenanceFatalErrorPropagates() {
        val fatal = OutOfMemoryError("cache maintenance exhausted memory")
        var observed: OutOfMemoryError? = null

        try {
            runRegenerableCacheWrite { throw fatal }
        } catch (error: OutOfMemoryError) {
            observed = error
        }

        assertSame(fatal, observed)
    }
}
