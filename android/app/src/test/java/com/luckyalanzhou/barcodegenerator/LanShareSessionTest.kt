package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareSessionTest {
    @Test
    fun shareUrlDoesNotCarryAuthenticationToken() {
        val session = LanShareSession("http://192.168.1.23:18080")

        assertEquals("http://192.168.1.23:18080", session.shareUrl)
    }

    @Test
    fun legacySessionWithoutTokenKeepsBaseUrl() {
        val session = LanShareSession("http://192.168.1.23:18080")

        assertEquals(session.baseUrl, session.shareUrl)
    }
}
