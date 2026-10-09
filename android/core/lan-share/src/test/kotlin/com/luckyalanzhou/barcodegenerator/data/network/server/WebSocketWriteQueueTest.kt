package com.luckyalanzhou.barcodegenerator.data.network.server

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketWriteQueueTest {
    @Test fun messagesAreWrittenInOrder() {
        val timer = Executors.newSingleThreadScheduledExecutor()
        val values = CopyOnWriteArrayList<Int>()
        val completed = CountDownLatch(3)
        val queue = WebSocketWriteQueue(timer) { _, _ -> }
        try {
            repeat(3) { value -> queue.enqueue { values.add(value); completed.countDown() } }
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(0, 1, 2), values.toList())
        } finally { queue.close(); timer.shutdownNow() }
    }

    @Test fun blockedPeerTimesOutWithoutBlockingOtherPeer() {
        val timer = Executors.newSingleThreadScheduledExecutor()
        val blocked = CountDownLatch(1)
        val started = CountDownLatch(1)
        val aborted = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val reasons = CopyOnWriteArrayList<String>()
        val slow = WebSocketWriteQueue(timer, timeoutMs = 100) { reason, _ ->
            reasons.add(reason); blocked.countDown(); aborted.countDown()
        }
        val fast = WebSocketWriteQueue(timer) { _, _ -> }
        try {
            slow.enqueue { started.countDown(); blocked.await() }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            fast.enqueue { delivered.countDown() }
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
            assertTrue(aborted.await(2, TimeUnit.SECONDS))
            slow.close()
            assertEquals(listOf("write_timeout"), reasons.toList())
        } finally { blocked.countDown(); slow.close(); fast.close(); timer.shutdownNow() }
    }

    @Test fun queuedMessagesAreBoundedAndOverflowAbortsOnce() {
        val timer = Executors.newSingleThreadScheduledExecutor()
        val blocked = CountDownLatch(1)
        val started = CountDownLatch(1)
        val reasons = CopyOnWriteArrayList<String>()
        val queue = WebSocketWriteQueue(timer, capacity = 1) { reason, _ ->
            reasons.add(reason); blocked.countDown()
        }
        try {
            queue.enqueue { started.countDown(); blocked.await() }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            queue.enqueue { error("Queued work should be discarded") }
            queue.enqueue { error("Overflow must not run") }
            queue.close()
            assertEquals(listOf("queue_overflow"), reasons.toList())
        } finally { blocked.countDown(); queue.close(); timer.shutdownNow() }
    }

    @Test fun writeFailureClosesQueueAndPreservesCause() {
        val timer = Executors.newSingleThreadScheduledExecutor()
        val aborted = CountDownLatch(1)
        val reasons = CopyOnWriteArrayList<String>()
        val queue = WebSocketWriteQueue(timer) { reason, error ->
            reasons.add("$reason:${error?.message}"); aborted.countDown()
        }
        try {
            queue.enqueue { throw java.io.IOException("closed peer") }
            assertTrue(aborted.await(2, TimeUnit.SECONDS))
            queue.close()
            assertEquals(listOf("write_failed:closed peer"), reasons.toList())
        } finally { queue.close(); timer.shutdownNow() }
    }
}
