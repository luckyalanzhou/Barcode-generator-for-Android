package com.luckyalanzhou.barcodegenerator.data.network.server

import java.security.MessageDigest
import java.util.Locale

/** A short code is only an entry method; successful pairing redirects to the strong session URL. */
internal class LanShareManualCodeGate(
    private val code: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Result { ACCEPTED, INVALID, TOO_MANY_ATTEMPTS }

    private data class Attempts(val firstAt: Long, val failures: Int)
    private val attemptsByAddress = HashMap<String, Attempts>()

    @Synchronized
    fun check(remoteAddress: String, submitted: String?): Result {
        val time = now()
        attemptsByAddress.entries.removeAll { time - it.value.firstAt >= WINDOW_MS }
        val key = remoteAddress.ifBlank { "unknown" }
        val attempts = attemptsByAddress[key]
        if (attempts != null && attempts.failures >= MAX_FAILURES) return Result.TOO_MANY_ATTEMPTS
        if (attempts == null && attemptsByAddress.size >= MAX_ADDRESSES) return Result.TOO_MANY_ATTEMPTS

        val candidate = submitted?.takeIf { it.length == 4 }?.uppercase(Locale.ROOT)
        if (candidate != null && MessageDigest.isEqual(
                code.toByteArray(Charsets.US_ASCII),
                candidate.toByteArray(Charsets.US_ASCII),
            )) {
            attemptsByAddress.remove(key)
            return Result.ACCEPTED
        }
        attemptsByAddress[key] = Attempts(attempts?.firstAt ?: time, (attempts?.failures ?: 0) + 1)
        return Result.INVALID
    }

    private companion object {
        const val MAX_FAILURES = 5
        const val MAX_ADDRESSES = 512
        const val WINDOW_MS = 5 * 60 * 1000L
    }
}
