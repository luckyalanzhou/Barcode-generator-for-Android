package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareBrowserPresence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareBrowserPresenceTest {
    @Test
    fun connectionRemainsActiveDuringGraceWindowAndExpiresAfterward() {
        var nowNanos = 1L
        val presence = LanShareBrowserPresence(
            nowNanos = { nowNanos },
            timeoutNanos = 10_000_000_000L,
        )

        assertFalse(presence.isConnected())
        presence.markSeen()
        assertTrue(presence.isConnected())

        nowNanos += 9_999_999_999L
        assertTrue(presence.isConnected())

        nowNanos += 1L
        assertFalse(presence.isConnected())
    }

    @Test
    fun freshBrowserRequestRestoresAnExpiredConnection() {
        var nowNanos = 1L
        val presence = LanShareBrowserPresence(
            nowNanos = { nowNanos },
            timeoutNanos = 10_000_000_000L,
        )

        presence.markSeen()
        nowNanos += 10_000_000_000L
        assertFalse(presence.isConnected())

        presence.markSeen()
        assertTrue(presence.isConnected())
    }
}
