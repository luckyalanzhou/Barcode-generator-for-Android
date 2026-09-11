package com.luckyalanzhou.barcodegenerator

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareFileIdentityTest {
    @Test
    fun lanShareAcceptsOnly192168RouterIpv4() {
        assertEquals(true, LanShareManager.isRouterLanHost("192.168.1.1"))
        assertEquals(false, LanShareManager.isRouterLanHost("10.0.0.1"))
        assertEquals(false, LanShareManager.isRouterLanHost("172.16.0.1"))
        assertEquals(false, LanShareManager.isRouterLanHost("127.0.0.1"))
    }

    @Test
    fun browserFileKeepsClientIdentityAndOriginalName() {
        val file = File("web_123456_cclient123_photo.jpg")

        val record = toLanShareFile(file, "peer")

        assertEquals("browser:cclient123", record.sender)
        assertEquals("photo.jpg", record.name)
    }

    @Test
    fun appFileKeepsOriginalName() {
        val file = File("app_123456_report.pdf")

        val record = toLanShareFile(file, "peer")

        assertEquals("app", record.sender)
        assertEquals("report.pdf", record.name)
    }
}
