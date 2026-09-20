package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareSessionTest {
    @Test
    fun shareUrlCarriesSessionToken() {
        val session = LanShareSession("http://192.168.1.23:18080", "session-token")

        assertEquals("http://192.168.1.23:18080/?token=session-token", session.shareUrl)
    }

    @Test
    fun legacySessionWithoutTokenKeepsBaseUrl() {
        val session = LanShareSession("http://192.168.1.23:18080")

        assertEquals(session.baseUrl, session.shareUrl)
    }
}
