package com.luckyalanzhou.barcodegenerator.presentation.shared


import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive

/** 串行执行持久化写入，并将普通写入失败作为可观察结果返回。 */
internal class PersistenceWriteQueue(
    private val writeScope: CoroutineScope,
) {
    private val lock = Any()
    private var tail: CompletableDeferred<Unit>? = null
    private var pendingWrites = 0
    private var idleSignal = CompletableDeferred(Unit)

    fun enqueue(write: suspend () -> Unit): Deferred<Result<Unit>> {
        val next: Deferred<Result<Unit>>
        val completion: CompletableDeferred<Unit>
        synchronized(lock) {
            val previous = tail
            if (pendingWrites == 0) idleSignal = CompletableDeferred()
            pendingWrites++
            completion = CompletableDeferred()
            next = writeScope.async(Dispatchers.IO) {
                // 串行等待完成信号，不依赖上次的结果或异常，避免单次写入阻断后续快照。
                previous?.await()
                // 父作用域取消通知可能尚未传到排队子协程；检查队列作用域，避免此时启动写入。
                writeScope.ensureActive()
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
                pendingWrites--
                if (pendingWrites == 0) idleSignal.complete(Unit)
            }
        }
        return next
    }

    /**
     * 等待调用时已经排队的所有写入结束。
     * 写入失败可通过 enqueue 返回的 Deferred 获取，但不会在此处抛出：
     * 过期写入失败不应导致下次启动时丢弃仍可读取的 Room 数据。
     */
    suspend fun awaitIdle() {
        while (true) {
            val currentIdleSignal = synchronized(lock) {
                if (pendingWrites == 0) null else idleSignal
            } ?: return
            currentIdleSignal.await()
        }
    }
}
