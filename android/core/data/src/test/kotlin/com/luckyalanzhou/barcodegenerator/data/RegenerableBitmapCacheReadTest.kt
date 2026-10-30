package com.luckyalanzhou.barcodegenerator.data

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RegenerableBitmapCacheReadTest {
    @Test
    fun ordinaryDiskReadFailureBecomesCacheMiss() {
        assertNull(runRegenerableCacheRead<String> { throw SecurityException("cache read denied") })
    }

    @Test
    fun successfulDiskReadPreservesTheCachedValue() {
        assertEquals("cached", runRegenerableCacheRead { "cached" })
    }

    @Test
    fun diskReadCancellationPropagatesUnchanged() {
        val cancellation = CancellationException("cache read cancelled")
        val observed = try {
            runRegenerableCacheRead<String> { throw cancellation }
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, observed)
    }

    @Test
    fun diskReadFatalErrorPropagatesUnchanged() {
        val fatal = OutOfMemoryError("cache read exhausted memory")
        val observed = try {
            runRegenerableCacheRead<String> { throw fatal }
            null
        } catch (error: OutOfMemoryError) {
            error
        }

        assertSame(fatal, observed)
    }
}
