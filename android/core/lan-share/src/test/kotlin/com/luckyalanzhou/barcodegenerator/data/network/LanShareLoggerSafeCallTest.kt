package com.luckyalanzhou.barcodegenerator.data.network

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareLoggerSafeCallTest {
    @Test
    fun ordinaryLoggerFailureDoesNotEscapeSuccessfulOperation() {
        val logger = AppLogger { _, _, _ -> throw IOException("diagnostic sink unavailable") }

        logger.recordSafely("test", "operation succeeded")
    }

    @Test
    fun ordinaryLoggerFailureIsSuppressedOnOriginalOperationFailure() {
        val primary = IOException("network failed")
        val diagnostic = IllegalStateException("diagnostic sink unavailable")
        val logger = AppLogger { _, _, _ -> throw diagnostic }

        logger.recordSafely("test", "operation failed", primaryError = primary)

        assertEquals(1, primary.suppressed.size)
        assertSame(diagnostic, primary.suppressed.single())
    }

    @Test
    fun loggerCancellationPropagates() {
        val cancellation = CancellationException("diagnostic cancellation")
        val logger = AppLogger { _, _, _ -> throw cancellation }

        val failure = runCatching { logger.recordSafely("test", "operation") }.exceptionOrNull()

        assertSame(cancellation, failure)
    }

    @Test
    fun loggerInterruptionPropagatesAndRestoresThreadFlag() {
        val interrupted = Thread.currentThread().isInterrupted
        val interruption = InterruptedException("diagnostic interruption")
        val logger = AppLogger { _, _, _ -> throw interruption }
        try {
            Thread.interrupted()

            val failure = runCatching { logger.recordSafely("test", "operation") }.exceptionOrNull()

            assertSame(interruption, failure)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    @Test
    fun fatalErrorPropagates() {
        val fatal = LinkageError("fatal logger failure")
        val logger = AppLogger { _, _, _ -> throw fatal }

        val failure = runCatching { logger.recordSafely("test", "operation") }.exceptionOrNull()

        assertSame(fatal, failure)
    }
}
