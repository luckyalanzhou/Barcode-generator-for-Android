package com.luckyalanzhou.barcodegenerator.infrastructure.barcode

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BarcodeEncodingRecoveryTest {
    @Test
    fun successfulEncodingReturnsTheEncodedValue() {
        var logged: Exception? = null

        val result = captureRecoverableBarcodeEncoding(
            operation = { "encoded" },
            onFailure = { logged = it },
        )

        assertEquals("encoded", result)
        assertNull(logged)
    }

    @Test
    fun ordinaryEncodingExceptionIsLoggedAndReturnedAsMissingImage() {
        val encodingError = IllegalArgumentException("不支持的条码内容")
        var logged: Exception? = null

        val result = captureRecoverableBarcodeEncoding<String>(
            operation = { throw encodingError },
            onFailure = { logged = it },
        )

        assertNull(result)
        assertSame(encodingError, logged)
    }

    @Test
    fun cancellationIsNotConvertedToMissingImage() {
        val cancellation = CancellationException("渲染任务已取消")
        var caught: CancellationException? = null

        try {
            captureRecoverableBarcodeEncoding<String>(
                operation = { throw cancellation },
                onFailure = {},
            )
        } catch (error: CancellationException) {
            caught = error
        }

        assertSame(cancellation, caught)
    }

    @Test
    fun fatalErrorIsNotConvertedToMissingImage() {
        val fatalError = AssertionError("条码渲染内存错误")
        var caught: AssertionError? = null

        try {
            captureRecoverableBarcodeEncoding<String>(
                operation = { throw fatalError },
                onFailure = {},
            )
        } catch (error: AssertionError) {
            caught = error
        }

        assertSame(fatalError, caught)
    }
}
