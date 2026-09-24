package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanShareSessionTest {
    @Test
    fun shareUrlCarriesSessionToken() {
        val session = LanShareSession("http://192.168.1.23:18080", "0123456789abcdefghijAB")

        assertEquals("http://192.168.1.23:18080/?token=0123456789abcdefghijAB", session.shareUrl)
        assertEquals(session, LanShareSession.fromShareUrl(session.shareUrl))
    }

    @Test
    fun bareOrMalformedAddressesCannotJoin() {
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080"))
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080/?token=short"))
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080/?token=0123456789abcdefghijAB&other=1"))
        assertNull(LanShareSession.fromShareUrl("http://example.com:18080/?token=0123456789abcdefghijAB"))
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080/api/files?token=0123456789abcdefghijAB"))
    }
}
