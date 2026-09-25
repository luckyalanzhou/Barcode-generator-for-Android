package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareSecurityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun manualCodeIsHostOnlyAndDoesNotChangeQrLink() {
        val session = LanShareSession("http://192.168.1.23:18080", "0123456789abcdefghijAB", "A7B2")

        assertEquals("A7B2", session.manualCode)
        assertEquals("http://192.168.1.23:18080/?token=0123456789abcdefghijAB", session.shareUrl)
        assertNull(LanShareSession.fromShareUrl(session.shareUrl)?.manualCode)
    }

    @Test
    fun encryptedAppPayloadCarriesHttpsAddressAndSessionCertificatePin() {
        val fingerprint = "a".repeat(64)
        val session = LanShareSession(
            baseUrl = "https://192.168.1.23:18492",
            accessToken = "0123456789abcdefghijAB",
            securityMode = LanShareSecurityMode.ENCRYPTED_APP,
            certificateFingerprint = fingerprint,
        )

        assertTrue(session.shareUrl.startsWith("barcodegenerator://lan-share?"))
        assertFalse(session.shareUrl.contains("https://192.168.1.23:18492"))
        assertEquals(session, LanShareSession.fromShareUrl(session.shareUrl))
    }

    @Test
    fun encryptedAppPayloadRejectsMissingPinMalformedAddressAndExtraParameters() {
        val valid = LanShareSession(
            baseUrl = "https://192.168.1.23:18492",
            accessToken = "0123456789abcdefghijAB",
            securityMode = LanShareSecurityMode.ENCRYPTED_APP,
            certificateFingerprint = "b".repeat(64),
        ).shareUrl

        assertNull(LanShareSession.fromShareUrl(valid.substringBefore("&pin=")))
        assertNull(LanShareSession.fromShareUrl(valid.replace("pin=${"b".repeat(64)}", "pin=short")))
        assertNull(LanShareSession.fromShareUrl("$valid&other=1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun encryptedAppSessionCannotUseHttpOrOmitCertificatePin() {
        LanShareSession(
            baseUrl = "http://192.168.1.23:18492",
            accessToken = "0123456789abcdefghijAB",
            securityMode = LanShareSecurityMode.ENCRYPTED_APP,
            certificateFingerprint = null,
        )
    }
}
