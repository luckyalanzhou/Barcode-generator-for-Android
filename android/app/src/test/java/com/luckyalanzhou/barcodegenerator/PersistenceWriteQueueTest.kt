package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.presentation.shared.PersistenceWriteQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
