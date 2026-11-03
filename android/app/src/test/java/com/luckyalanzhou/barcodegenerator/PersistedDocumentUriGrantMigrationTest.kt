package com.luckyalanzhou.barcodegenerator

import android.content.Intent
import android.net.Uri
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistedDocumentUriGrantMigrationTest {
    @Test
    fun migrationRunsOnDaemonWorkerInsteadOfCallingThread() {
        val callingThread = Thread.currentThread()
        val migrationThread = AtomicReference<Thread>()
        val migrationFailure = AtomicReference<Exception?>()
        val worker = startDocumentUriGrantMigrationAsync(
            operation = { migrationThread.set(Thread.currentThread()) },
            onFailure = migrationFailure::set,
        )

        worker.join(TimeUnit.SECONDS.toMillis(2))

        assertTrue("migration worker should finish", !worker.isAlive)
        assertTrue("migration worker should be a daemon", worker.isDaemon)
        assertNotSame(callingThread, migrationThread.get())
        assertNull(migrationFailure.get())
    }

    @Test
    fun recoverablePlatformExceptionIsReturnedForRetryHandling() {
        val failure = IllegalStateException("provider unavailable")

        val result = captureRecoverableDocumentUriGrantOperation<Int> { throw failure }

        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun cancellationDuringMigrationOperationPropagates() {
        val cancellation = CancellationException("migration cancelled")
        val thrown = try {
            captureRecoverableDocumentUriGrantOperation<Int> { throw cancellation }
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun fatalErrorDuringMigrationReadPropagates() {
        val fatal = AssertionError("platform invariant failed")
        val thrown = try {
            captureRecoverableDocumentUriGrantOperation<Int> { throw fatal }
            null
        } catch (error: AssertionError) {
            error
        }

        assertSame(fatal, thrown)
    }

    @Test
    fun releasesOnlyGrantedModesAndContinuesAfterAnIndividualFailure() {
        val first = Uri.parse("content://provider/first")
        val failed = Uri.parse("content://provider/failed")
        val last = Uri.parse("content://provider/last")
        val afterFailure = Uri.parse("content://provider/after-failure")
        val released = mutableListOf<Pair<Uri, Int>>()

        val failures = releasePersistedDocumentUriGrants(
            listOf(
                PersistedDocumentUriGrant(first, canRead = true, canWrite = false),
                PersistedDocumentUriGrant(failed, canRead = true, canWrite = true),
                PersistedDocumentUriGrant(last, canRead = false, canWrite = false),
                PersistedDocumentUriGrant(afterFailure, canRead = true, canWrite = false),
            ),
        ) { uri, modeFlags ->
            released += uri to modeFlags
            if (uri == failed) error("provider rejected release")
        }

        assertEquals(1, failures)
        assertEquals(
            listOf(
                first to Intent.FLAG_GRANT_READ_URI_PERMISSION,
                failed to (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
                afterFailure to Intent.FLAG_GRANT_READ_URI_PERMISSION,
            ),
            released,
        )
    }

    @Test
    fun fatalErrorDuringReleasePropagatesInsteadOfBeingCountedAsRecoverableFailure() {
        val fatal = AssertionError("provider invariant failed")
        val thrown = try {
            releasePersistedDocumentUriGrants(
                listOf(PersistedDocumentUriGrant(Uri.parse("content://provider/item"), canRead = true, canWrite = false)),
            ) { _, _ -> throw fatal }
            null
        } catch (error: AssertionError) {
            error
        }

        assertSame(fatal, thrown)
    }
}
