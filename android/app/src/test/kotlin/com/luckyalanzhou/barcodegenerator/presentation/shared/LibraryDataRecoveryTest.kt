package com.luckyalanzhou.barcodegenerator.presentation.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDataRecoveryTest {
    @Test
    fun ordinaryRetryNotificationFailureDoesNotReplaceReadFailure() {
        val readFailure = IllegalStateException("snapshot unavailable")
        val notificationFailure = IllegalStateException("logger unavailable")

        runSnapshotRetryNotification(readFailure) { throw notificationFailure }

        assertSame(notificationFailure, readFailure.suppressed.single())
    }

    @Test
    fun retryNotificationCancellationPropagates() {
        val readFailure = IllegalStateException("snapshot unavailable")
        val cancellation = CancellationException("load cancelled")
        val thrown = runCatching {
            runSnapshotRetryNotification(readFailure) { throw cancellation }
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertTrue(readFailure.suppressed.isEmpty())
    }

    @Test
    fun retryNotificationFatalErrorPropagates() {
        val readFailure = IllegalStateException("snapshot unavailable")
        val fatalError = AssertionError("fatal logger failure")
        val thrown = runCatching {
            runSnapshotRetryNotification(readFailure) { throw fatalError }
        }.exceptionOrNull()

        assertSame(fatalError, thrown)
        assertTrue(readFailure.suppressed.isEmpty())
    }

    @Test
    fun retryNotificationDoesNotAddReadFailureAsItsOwnSuppressedException() {
        val readFailure = IllegalStateException("snapshot unavailable")

        runSnapshotRetryNotification(readFailure) { throw readFailure }

        assertTrue(readFailure.suppressed.isEmpty())
    }

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
