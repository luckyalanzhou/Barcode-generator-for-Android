package com.luckyalanzhou.barcodegenerator.ui.support.logging

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** 普通诊断限量排队、单线程写入；队列溢出明确计数，不拖住 UI，也不无限占用内存。 */
internal class DiagnosticWriteQueue(
    capacity: Int = 256,
    private val onFailure: (Throwable) -> Unit,
    private val onDropped: (Int) -> Unit,
) : AutoCloseable {
    private val dropped = AtomicInteger()
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(capacity), { task -> Thread(task, "barcode-diagnostics").apply { isDaemon = true } })

    fun submit(write: () -> Unit) {
        try { executor.execute { drainDropped(); safely(write) } }
        catch (_: RejectedExecutionException) { dropped.incrementAndGet() }
    }

    /** 导出前设置顺序屏障；超时须在导出文件中说明，不能假装所有记录已落盘。 */
    fun flush(timeoutMs: Long = 2_000): Boolean {
        val barrier = FutureTask(java.util.concurrent.Callable { drainDropped() })
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        try { executor.execute(barrier) }
        catch (_: RejectedExecutionException) {
            if (!executor.queue.offer(barrier, timeoutMs, TimeUnit.MILLISECONDS)) return false
        }
        return try {
            barrier.get((deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
            true
        } catch (_: java.util.concurrent.TimeoutException) { false }
    }

    private fun drainDropped() {
        val count = dropped.getAndSet(0)
        if (count > 0) safely { onDropped(count) }
    }
    private fun safely(write: () -> Unit) {
        try { write() } catch (error: Throwable) { onFailure(error) }
    }
    override fun close() { flush(); executor.shutdown() }
}
