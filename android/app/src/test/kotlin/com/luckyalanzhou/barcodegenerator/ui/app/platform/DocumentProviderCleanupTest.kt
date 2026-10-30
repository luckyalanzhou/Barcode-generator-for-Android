package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentProviderCleanupTest {
    @Test
    fun cleanupRunsOnIoAndCompletesWhenCallerIsCancelled() = runBlocking {
        val callerThread = Thread.currentThread()
        val cleanupThread = AtomicReference<Thread?>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                currentCoroutineContext().cancel(CancellationException("caller cancelled"))
                yield()
            } catch (cancelled: CancellationException) {
                runDocumentProviderCleanup {
                    cleanupThread.set(Thread.currentThread())
                }
                throw cancelled
            }
        }

        job.join()

        assertTrue(job.isCancelled)
        assertNotSame(callerThread, cleanupThread.get())
    }

    @Test
    fun cleanupResultCapturesOrdinaryProviderFailure() = runBlocking {
        val failure = SecurityException("provider denied deletion")

        val result = runDocumentProviderCleanupResult { throw failure }

        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun cleanupResultPropagatesCancellation() = runBlocking {
        val cancellation = CancellationException("cleanup cancelled")
        val observed = try {
            runDocumentProviderCleanupResult { throw cancellation }
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, observed)
    }

    @Test
    fun cleanupResultPropagatesFatalError() = runBlocking {
        val fatal = OutOfMemoryError("provider cleanup exhausted memory")
        val observed = try {
            runDocumentProviderCleanupResult { throw fatal }
            null
        } catch (error: OutOfMemoryError) {
            error
        }

        assertSame(fatal, observed)
    }
}
