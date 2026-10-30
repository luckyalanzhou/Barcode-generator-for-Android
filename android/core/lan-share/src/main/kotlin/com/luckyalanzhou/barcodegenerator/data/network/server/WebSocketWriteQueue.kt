package com.luckyalanzhou.barcodegenerator.data.network.server

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 每个设备独立、限量、顺序发送；写入超时直接关闭底层 TCP，而不是等待阻塞的关闭帧。 */
internal class WebSocketWriteQueue(
    private val timer: ScheduledExecutorService,
    private val timeoutMs: Long = 5_000,
    capacity: Int = 32,
    private val onAborted: (String, Throwable?) -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean()
    private val snapshotPending = AtomicBoolean()
    private val writer = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(capacity),
        { task -> Thread(task, "lan-share-peer-write").apply { isDaemon = true } })

    fun enqueue(write: () -> Unit) {
        enqueueInternal(write)
    }

    /** 每个连接最多保留一个排队中或正在发送的全量快照。 */
    fun enqueueSnapshot(write: () -> Unit): Boolean {
        if (!snapshotPending.compareAndSet(false, true)) return false
        val accepted = enqueueInternal(write) { snapshotPending.set(false) }
        if (!accepted) snapshotPending.set(false)
        return accepted
    }

    private fun enqueueInternal(write: () -> Unit, onFinished: (() -> Unit)? = null): Boolean {
        if (closed.get()) return false
        try {
            writer.execute {
                if (closed.get()) return@execute
                val deadline = try { timer.schedule({ abort("write_timeout") }, timeoutMs, TimeUnit.MILLISECONDS) }
                    catch (_: RejectedExecutionException) { abort("server_stopped"); return@execute }
                try { write() }
                catch (error: Exception) { abort("write_failed", error) }
                finally {
                    deadline.cancel(false)
                    onFinished?.invoke()
                }
            }
            return true
        } catch (_: RejectedExecutionException) {
            abort("queue_overflow")
            return false
        }
    }

    private fun abort(reason: String, error: Throwable? = null) {
        if (!closed.compareAndSet(false, true)) return
        try { onAborted(reason, error) }
        finally { writer.shutdownNow() }
    }
    override fun close() = abort("closed")
}
