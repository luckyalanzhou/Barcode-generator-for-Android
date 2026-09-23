package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.presentation.shared.PersistenceWriteQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
            val queue = PersistenceWriteQueue()
            val order = mutableListOf<Int>()

            queue.enqueue(scope) {
                delay(20)
                order += 1
            }
            queue.enqueue(scope) { order += 2 }

            queue.awaitIdle()

            assertEquals(listOf(1, 2), order)
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun failedWriteIsObservableAndDoesNotPreventNextWrite() {
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PersistenceWriteQueue()
            val order = mutableListOf<Int>()

            val failed = queue.enqueue(scope) {
                error("expected failure")
            }
            val succeeded = queue.enqueue(scope) { order += 2 }

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
            val queue = PersistenceWriteQueue()
            val order = mutableListOf<Int>()

            queue.enqueue(scope) { error("expected failure") }
            queue.enqueue(scope) { order += 2 }

            queue.awaitIdle()

            assertEquals(listOf(2), order)
            scope.coroutineContext[Job]?.cancel()
        }
    }
}
