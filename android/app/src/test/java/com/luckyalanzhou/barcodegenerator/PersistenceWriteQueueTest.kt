package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.presentation.shared.PersistenceWriteQueue
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceWriteQueueTest {
    @Test
    fun awaitIdleWaitsForQueuedWritesInOrder() {
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PersistenceWriteQueue(scope)
            val order = mutableListOf<Int>()

            queue.enqueue {
                delay(20)
                order += 1
            }
            queue.enqueue { order += 2 }

            queue.awaitIdle()

            assertEquals(listOf(1, 2), order)
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun failedWriteIsObservableAndDoesNotPreventNextWrite() {
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PersistenceWriteQueue(scope)
            val order = mutableListOf<Int>()

            val failed = queue.enqueue {
                error("expected failure")
            }
            val succeeded = queue.enqueue { order += 2 }

            assertTrue(failed.await().isFailure)
            assertTrue(succeeded.await().isSuccess)
            assertEquals(listOf(2), order)
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun cancellationFromWriteRemainsCancellation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = PersistenceWriteQueue(scope)
        val cancellation = CancellationException("write cancelled")

        try {
            val thrown = try {
                queue.enqueue { throw cancellation }.await()
                null
            } catch (error: Throwable) {
                error
            }

            assertTrue(thrown is CancellationException)
            assertEquals(cancellation.message, thrown?.message)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun independentlyCancelledWriteDoesNotPreventNextSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = PersistenceWriteQueue(scope)
        val cancellation = CancellationException("write cancelled")
        var nextSnapshotAttempted = false

        try {
            val cancelled = queue.enqueue { throw cancellation }
            val next = queue.enqueue { nextSnapshotAttempted = true }

            val thrown = runCatching { cancelled.await() }.exceptionOrNull()
            assertTrue(thrown is CancellationException)
            assertEquals(cancellation.message, thrown?.message)
            assertTrue(next.await().isSuccess)
            assertTrue(nextSnapshotAttempted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingWriteScopeStopsQueuedWritesAndReleasesIdleWaiters() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = PersistenceWriteQueue(scope)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var nextSnapshotAttempted = false

        val active = queue.enqueue {
            started.complete(Unit)
            release.await()
        }
        val queued = queue.enqueue { nextSnapshotAttempted = true }
        started.await()
        scope.cancel()

        queue.awaitIdle()

        assertTrue("active write should be cancelled", active.isCancelled)
        assertTrue("queued write should be cancelled", queued.isCancelled)
        assertFalse("queued write started after its scope was cancelled", nextSnapshotAttempted)
    }

    @Test
    fun awaitIdleWaitsForEarlierWriteCancellationCleanup() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = PersistenceWriteQueue(scope)
        val started = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        var nextSnapshotAttempted = false

        val active = queue.enqueue {
            started.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                withContext(NonCancellable) {
                    cleanupStarted.complete(Unit)
                    finishCleanup.await()
                }
            }
        }
        val queued = queue.enqueue { nextSnapshotAttempted = true }
        started.await()
        val idle = async { queue.awaitIdle() }
        yield()

        try {
            scope.cancel()
            cleanupStarted.await()
            runCatching { queued.await() }

            assertFalse("idle must wait for the active write's cancellation cleanup", idle.isCompleted)
        } finally {
            finishCleanup.complete(Unit)
            scope.cancel()
        }

        idle.await()
        assertTrue(active.isCancelled)
        assertTrue(queued.isCancelled)
        assertFalse(nextSnapshotAttempted)
    }

    @Test
    fun fatalWriteErrorIsNotReportedAsRecoverableFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = PersistenceWriteQueue(scope)
        val fatalError = AssertionError("fatal write error")

        try {
            val thrown = try {
                queue.enqueue { throw fatalError }.await()
                null
            } catch (error: Throwable) {
                error
            }

            assertTrue(thrown is AssertionError)
            assertEquals(fatalError.message, thrown?.message)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun awaitIdleReturnsFailureWithoutBlockingLaterStartupReads() {
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PersistenceWriteQueue(scope)
            val order = mutableListOf<Int>()

            queue.enqueue { error("expected failure") }
            queue.enqueue { order += 2 }

            queue.awaitIdle()

            assertEquals(listOf(2), order)
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun queuedWriteSurvivesTheRequestingScopeEnding() = runBlocking {
        val requester = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = PersistenceWriteQueue(writeScope)
        val release = CompletableDeferred<Unit>()
        var completed = false

        requester.launch { queue.enqueue { release.await(); completed = true } }.join()
        requester.cancel()
        release.complete(Unit)
        queue.awaitIdle()

        assertTrue(completed)
        writeScope.cancel()
    }
}
