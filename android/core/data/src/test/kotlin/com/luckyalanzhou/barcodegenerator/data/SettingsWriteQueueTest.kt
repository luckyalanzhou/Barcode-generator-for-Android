package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.awaitSettingsCommit
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SettingsWriteQueueTest {
    @Test
    fun failedWriteIsObservableWithoutUncaughtExceptionAndRetryStillCommits() = runBlocking {
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> uncaught.add(error) })
        try {
            val queue = SettingsWriteQueue(scope)
            val failure = queue.enqueue { throw IOException("disk unavailable") }
            val committed = mutableListOf<Int>()
            val retry = queue.enqueue { committed.add(2) }
            assertTrue(runCatching { failure.awaitSettingsCommit() }.exceptionOrNull() is IOException)
            retry.awaitSettingsCommit()
            assertEquals(listOf(2), committed)
            assertTrue(uncaught.isEmpty())
        } finally { scope.cancel() }
    }

    @Test
    fun pendingWriteOrdersNextCommitAndCancellingWaitDoesNotCancelSave() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val queue = SettingsWriteQueue(scope)
            val gate = CompletableDeferred<Unit>()
            val order = mutableListOf<Int>()
            val first = queue.enqueue { gate.await(); order.add(1) }
            val second = queue.enqueue { order.add(2) }
            val waiter = launch(start = CoroutineStart.UNDISPATCHED) { first.awaitSettingsCommit() }
            waiter.cancelAndJoin()
            assertFalse(first.isCancelled)
            assertFalse(second.isCompleted)
            gate.complete(Unit)
            second.awaitSettingsCommit()
            assertEquals(listOf(1, 2), order)
        } finally { scope.cancel() }
    }
}
