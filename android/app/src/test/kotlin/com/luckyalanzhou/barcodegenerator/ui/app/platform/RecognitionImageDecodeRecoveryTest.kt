package com.luckyalanzhou.barcodegenerator.ui.app.platform

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RecognitionImageDecodeRecoveryTest {
    @Test
    fun successfulDecodeReturnsBitmapAndDoesNotRunFailureCleanup() {
        var cleanupCount = 0
        var logged: Exception? = null

        val result = captureRecoverableRecognitionImageDecode(
            operation = { "bitmap" },
            cleanup = { cleanupCount++ },
            onFailure = { logged = it },
        )

        assertEquals("bitmap", result)
        assertEquals(0, cleanupCount)
        assertNull(logged)
    }

    @Test
    fun ordinaryDecodeExceptionCleansPartialResultAndReturnsNoImage() {
        val decodeError = IllegalArgumentException("无法解码图片")
        var cleanupCount = 0
        var logged: Exception? = null

        val result = captureRecoverableRecognitionImageDecode<String>(
            operation = { throw decodeError },
            cleanup = { cleanupCount++ },
            onFailure = { logged = it },
        )

        assertNull(result)
        assertEquals(1, cleanupCount)
        assertSame(decodeError, logged)
    }

    @Test
    fun cleanupReusingDecodeFailureDoesNotMaskOriginalFailure() {
        val decodeError = IllegalArgumentException("图片解码失败")
        var logged: Exception? = null

        val result = captureRecoverableRecognitionImageDecode<String>(
            operation = { throw decodeError },
            cleanup = { throw decodeError },
            onFailure = { logged = it },
        )

        assertNull(result)
        assertSame(decodeError, logged)
        assertEquals(0, decodeError.suppressed.size)
    }

    @Test
    fun cancellationCleansPartialResultAndPropagates() {
        val cancellation = CancellationException("Activity 已结束")
        var cleanupCount = 0
        var caught: CancellationException? = null

        try {
            captureRecoverableRecognitionImageDecode<String>(
                operation = { throw cancellation },
                cleanup = { cleanupCount++ },
                onFailure = {},
            )
        } catch (error: CancellationException) {
            caught = error
        }

        assertSame(cancellation, caught)
        assertEquals(1, cleanupCount)
    }

    @Test
    fun fatalErrorCleansPartialResultAndPropagates() {
        val fatalError = AssertionError("图片内存分配失败")
        var cleanupCount = 0
        var caught: AssertionError? = null

        try {
            captureRecoverableRecognitionImageDecode<String>(
                operation = { throw fatalError },
                cleanup = { cleanupCount++ },
                onFailure = {},
            )
        } catch (error: AssertionError) {
            caught = error
        }

        assertSame(fatalError, caught)
        assertEquals(1, cleanupCount)
    }
}
