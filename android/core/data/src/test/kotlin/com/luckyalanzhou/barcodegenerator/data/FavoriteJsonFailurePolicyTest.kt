package com.luckyalanzhou.barcodegenerator.data

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FavoriteJsonFailurePolicyTest {
    @Test
    fun jsonSyntaxFailureKeepsExistingUserFacingMessage() {
        val failure = captureFailure {
            parseFavoriteJsonOrError<String> { throw JSONException("malformed JSON") }
        }

        assertEquals(IllegalStateException::class.java, failure?.javaClass)
        assertEquals("收藏文件不是有效 JSON", failure?.message)
    }

    @Test
    fun fatalErrorsPropagateInsteadOfBeingReportedAsInvalidJson() {
        val fatal = OutOfMemoryError("JSON parser allocation failed")
        val failure = captureFailure { parseFavoriteJsonOrError<String> { throw fatal } }

        assertSame(fatal, failure)
    }

    @Test
    fun nonJsonRuntimeExceptionsPropagateWithoutBeingReclassified() {
        val unexpected = IllegalStateException("unexpected parser failure")
        val failure = captureFailure { parseFavoriteJsonOrError<String> { throw unexpected } }

        assertSame(unexpected, failure)
    }

    private fun captureFailure(block: () -> Unit): Throwable? = try {
        block()
        null
    } catch (failure: Throwable) {
        failure
    }
}
