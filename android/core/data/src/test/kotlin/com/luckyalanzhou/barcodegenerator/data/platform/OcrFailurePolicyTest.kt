package com.luckyalanzhou.barcodegenerator.data.platform

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OcrFailurePolicyTest {
    @Test
    fun fatalErrorsPropagateInsteadOfBecomingEmptyRecognitionResults() {
        val fatal = OutOfMemoryError("OCR result allocation failed")
        val observed = try {
            ocrFallbackOrPropagate(
                fatal,
                fallback = { emptyList<String>() },
                propagate = { throw it },
            )
            null
        } catch (error: Throwable) {
            error
        }

        assertSame(fatal, observed)
    }

    @Test
    fun coroutineCancellationPropagatesInsteadOfBecomingAnEmptyResult() {
        val cancellation = CancellationException("OCR cancelled")
        val observed = try {
            ocrFallbackOrPropagate(
                cancellation,
                fallback = { emptyList<String>() },
                propagate = { throw it },
            )
            null
        } catch (error: Throwable) {
            error
        }

        assertSame(cancellation, observed)
    }

    @Test
    fun ordinaryRecognitionExceptionsKeepTheEmptyResultFallback() {
        val fallback = listOf("recoverable fallback")

        val result = ocrFallbackOrPropagate(
            IllegalStateException("recognizer unavailable"),
            fallback = { fallback },
            propagate = { throw it },
        )

        assertEquals(fallback, result)
    }
}
