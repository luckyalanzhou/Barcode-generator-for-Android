package com.luckyalanzhou.barcodegenerator.data.platform

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertSame
import org.junit.Test

class AppLoggerSafeCallTest {
    @Test
    fun ordinaryLoggerFailureDoesNotEscape() {
        val logger = AppLogger { _, _, _ -> throw IOException("diagnostic storage unavailable") }

        logger.recordSafely("test", "operation succeeded")
    }

    @Test
    fun loggerCancellationStillPropagates() {
        val cancellation = CancellationException("logger cancelled")
        val logger = AppLogger { _, _, _ -> throw cancellation }

        val thrown = runCatching { logger.recordSafely("test", "operation") }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }

    @Test
    fun fatalLoggerErrorStillPropagates() {
        val fatalError = LinkageError("logger runtime is unavailable")
        val logger = AppLogger { _, _, _ -> throw fatalError }

        val thrown = runCatching { logger.recordSafely("test", "operation") }.exceptionOrNull()

        assertSame(fatalError, thrown)
    }
}
