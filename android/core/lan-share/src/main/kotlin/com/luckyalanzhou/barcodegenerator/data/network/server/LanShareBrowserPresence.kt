package com.luckyalanzhou.barcodegenerator.data.network.server

/** Tracks recent browser activity using a monotonic clock and a grace window. */
internal class LanShareBrowserPresence(
    private val nowNanos: () -> Long = System::nanoTime,
    private val timeoutNanos: Long = DEFAULT_TIMEOUT_NANOS,
) {
    @Volatile private var lastSeenNanos: Long? = null

    fun markSeen() {
        lastSeenNanos = nowNanos()
    }

    fun isConnected(): Boolean {
        val lastSeen = lastSeenNanos ?: return false
        val elapsed = nowNanos() - lastSeen
        return elapsed in 0 until timeoutNanos
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val DEFAULT_TIMEOUT_NANOS = DEFAULT_TIMEOUT_MILLIS * NANOS_PER_MILLI
    }
}
