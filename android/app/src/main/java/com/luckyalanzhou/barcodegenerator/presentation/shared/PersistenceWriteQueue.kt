package com.luckyalanzhou.barcodegenerator.presentation.shared


import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/** 串行执行持久化写入，并将普通写入失败作为可观察结果返回。 */
internal class PersistenceWriteQueue(
    private val writeScope: CoroutineScope,
) {
    private val lock = Any()
    private var tail: CompletableDeferred<Unit>? = null

    fun enqueue(write: suspend () -> Unit): Deferred<Result<Unit>> {
        val next: Deferred<Result<Unit>>
        val completion: CompletableDeferred<Unit>
        synchronized(lock) {
            val previous = tail
            completion = CompletableDeferred()
            next = writeScope.async(Dispatchers.IO) {
                // 串行等待完成信号，不依赖上次的结果或异常，避免单次写入阻断后续快照。
                previous?.await()
                try {
                    write()
                    Result.success(Unit)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
            tail = completion
        }
        next.invokeOnCompletion {
            completion.complete(Unit)
            synchronized(lock) {
                if (tail === completion) tail = null
            }
        }
        return next
    }

    /**
     * Waits until all writes that were queued at the time of the call finish.
     * A failed write is observable through the Deferred returned by enqueue,
     * but is not thrown here:
     * a stale write must not make the next startup discard readable Room data.
     */
    suspend fun awaitIdle() {
        while (true) {
            val current = synchronized(lock) { tail } ?: return
            current.await()
            if (synchronized(lock) { tail } === current) return
        }
    }
}
