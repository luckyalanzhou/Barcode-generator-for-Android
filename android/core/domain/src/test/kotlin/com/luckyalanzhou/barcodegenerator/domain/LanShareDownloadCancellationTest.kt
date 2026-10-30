package com.luckyalanzhou.barcodegenerator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareDownloadCancellationTest {
    @Test
    fun cancellationInvokesAttachedResourceActionOnlyOnce() {
        val cancellation = LanShareDownloadCancellation()
        var disconnectCount = 0
        val disconnect: () -> Unit = { disconnectCount++ }

        cancellation.attach(disconnect)
        cancellation.cancel()
        cancellation.cancel()

        assertEquals(1, disconnectCount)
        assertTrue(cancellation.isCancelled)
    }

    @Test
    fun resourceAttachedAfterCancellationIsImmediatelyCancelled() {
        val cancellation = LanShareDownloadCancellation()
        var disconnectCount = 0

        cancellation.cancel()
        cancellation.attach { disconnectCount++ }

        assertEquals(1, disconnectCount)
    }

    @Test
    fun detachingCompletedRequestPreventsLaterDisconnect() {
        val cancellation = LanShareDownloadCancellation()
        var disconnectCount = 0
        val disconnect: () -> Unit = { disconnectCount++ }

        cancellation.attach(disconnect)
        cancellation.detach(disconnect)
        cancellation.cancel()

        assertEquals(0, disconnectCount)
    }
}
