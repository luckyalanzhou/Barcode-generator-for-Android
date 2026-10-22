package com.luckyalanzhou.barcodegenerator.presentation.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryDataRecoveryTest {
    @Test
    fun cancellationPropagatesWithoutBeingReportedAsReloadFailure() {
        val cancellation = CancellationException("view model cleared")
        var reportedFailure: Throwable? = null

        val thrown = runCatching {
            runBlocking {
                runLibraryDataRecovery(
                    load = { throw cancellation },
                    onFailure = { reportedFailure = it },
                )
            }
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertNull(reportedFailure)
    }

    @Test
    fun ordinaryReloadFailureIsReportedOnceAndNotRethrown() = runBlocking {
        val failure = IllegalStateException("snapshot unavailable")
        var reportedFailure: Throwable? = null

        runLibraryDataRecovery(
            load = { throw failure },
            onFailure = { reportedFailure = it },
        )

        assertSame(failure, reportedFailure)
    }

    @Test
    fun fatalErrorPropagatesWithoutBeingReportedAsReloadFailure() = runBlocking {
        val fatalError = AssertionError("fatal runtime failure")
        var reportedFailure: Throwable? = null

        val thrown = runCatching {
            runLibraryDataRecovery(
                load = { throw fatalError },
                onFailure = { reportedFailure = it },
            )
        }.exceptionOrNull()

        assertSame(fatalError, thrown)
        assertNull(reportedFailure)
    }
}
