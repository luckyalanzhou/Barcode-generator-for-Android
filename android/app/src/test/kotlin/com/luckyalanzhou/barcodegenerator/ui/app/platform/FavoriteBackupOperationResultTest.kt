package com.luckyalanzhou.barcodegenerator.ui.app.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteBackupOperationResultTest {
    @Test
    fun ordinaryFailureIsReturnedForExistingUserFeedback() = runBlocking {
        val failure = IllegalStateException("backup unavailable")

        val result = captureRecoverableFavoriteBackupOperation { throw failure }

        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingAnImportOrExportFailure() = runBlocking {
        val cancellation = CancellationException("activity destroyed")

        val thrown = runCatching {
            captureRecoverableFavoriteBackupOperation { throw cancellation }
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }

    @Test
    fun fatalErrorPropagatesInsteadOfBecomingAnImportOrExportFailure() = runBlocking {
        val fatalError = AssertionError("fatal backup failure")

        val thrown = runCatching {
            captureRecoverableFavoriteBackupOperation { throw fatalError }
        }.exceptionOrNull()

        assertSame(fatalError, thrown)
        assertTrue(thrown is AssertionError)
    }
}
