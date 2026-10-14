package com.luckyalanzhou.barcodegenerator.ui.support.logging

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DiagnosticWriteQueueTest {
    @Test fun writeRunsOffCallerAndFlushWaitsForOrderedRecords() {
        val caller = Thread.currentThread()
        val values = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()
        DiagnosticWriteQueue(onFailure = failures::add, onDropped = { fail("unexpected overflow") }).use { queue ->
            queue.submit { assertNotSame(caller, Thread.currentThread()); values.add(1) }
            queue.submit { throw IllegalStateException("failed sink") }
            queue.submit { values.add(2) }
            assertTrue(queue.flush())
            assertEquals(listOf(1, 2), values)
            assertEquals(1, failures.size)
        }
    }
    @Test fun overflowingQueueReportsOmittedRecordsAndFlushTimeout() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var omitted = 0
        val queue = DiagnosticWriteQueue(1, { throw AssertionError(it) }, { omitted += it })
        try {
            queue.submit { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            queue.submit { }
            queue.submit { fail("overflowed record must not execute") }
            assertFalse(queue.flush(10))
            release.countDown()
            assertTrue(queue.flush())
            assertEquals(1, omitted)
        } finally { release.countDown(); queue.close() }
    }
}
