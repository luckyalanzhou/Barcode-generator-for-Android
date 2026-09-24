package com.luckyalanzhou.barcodegenerator.presentation.shared


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Serializes persistence writes while keeping one failed write from cancelling
 * unrelated ViewModel work. Every queued operation returns an observable result.
 */
internal class PersistenceWriteQueue(
    private val writeScope: CoroutineScope,
) {
    private val lock = Any()
    private var tail: Deferred<Result<Unit>>? = null

    fun enqueue(write: suspend () -> Unit): Deferred<Result<Unit>> {
        val next: Deferred<Result<Unit>>
        synchronized(lock) {
            val previous = tail
            next = writeScope.async(Dispatchers.IO) {
                // A failed write must not prevent later snapshots from being attempted.
                previous?.await()
                runCatching { write() }
            }
            tail = next
        }
        next.invokeOnCompletion {
            synchronized(lock) {
                if (tail === next) tail = null
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
