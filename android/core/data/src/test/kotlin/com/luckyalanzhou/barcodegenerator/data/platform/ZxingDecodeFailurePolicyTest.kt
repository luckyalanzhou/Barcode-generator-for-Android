package com.luckyalanzhou.barcodegenerator.data.platform

import java.util.concurrent.CancellationException
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ZxingDecodeFailurePolicyTest {
    @Test
    fun fatalErrorsPropagateInsteadOfBeingReportedAsUnrecognizedImages() {
        val fatal = OutOfMemoryError("pixel allocation failed")
        var observed: OutOfMemoryError? = null

        try {
            decodeImageOrNull<String> { throw fatal }
        } catch (error: OutOfMemoryError) {
            observed = error
        }

        assertSame(fatal, observed)
    }

    @Test
    fun coroutineCancellationPropagatesInsteadOfBeingReportedAsUnrecognizedImages() {
        val cancellation = CancellationException("recognition cancelled")
        var observed: CancellationException? = null

        try {
            decodeImageOrNull<String> { throw cancellation }
        } catch (error: CancellationException) {
            observed = error
        }

        assertSame(cancellation, observed)
    }

    @Test
    fun ordinaryDecodeExceptionsRemainUnrecognizedImages() {
        val result = decodeImageOrNull<String> { throw IllegalArgumentException("invalid pixels") }

        assertNull(result)
    }
}
