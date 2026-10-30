package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanShareSessionTest {
    @Test
    fun shareUrlIsTheDirectSessionAddress() {
        val session = LanShareSession("http://192.168.1.23:18080")

        assertEquals("http://192.168.1.23:18080", session.shareUrl)
        assertEquals(session, LanShareSession.fromShareUrl(session.shareUrl))
    }

    @Test
    fun acceptsRootAddressesAndRejectsNonLanOrNonRootAddresses() {
        assertEquals(
            "http://192.168.1.23:18080",
            LanShareSession.fromShareUrl("http://192.168.1.23:18080/")?.baseUrl,
        )
        assertNull(LanShareSession.fromShareUrl("http://example.com:18080"))
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080/api/files"))
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080/?token=legacy"))
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080/#fragment"))
        assertNull(LanShareSession.fromShareUrl("https://192.168.1.23:18080"))
    }

    @Test
    fun shareUrlCarriesOnlyAWellFormedSessionToken() {
        val token = "A".repeat(43)
        val session = LanShareSession("http://192.168.1.23:18080", token)

        assertEquals("http://192.168.1.23:18080?token=$token", session.shareUrl)
        assertEquals(session, LanShareSession.fromShareUrl(session.shareUrl))
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080?token=short"))
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080?token=$token&token=$token"))
        assertNull(LanShareSession.fromShareUrl("http://192.168.1.23:18080?other=value"))
    }
}
