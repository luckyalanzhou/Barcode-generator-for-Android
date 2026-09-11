package com.luckyalanzhou.barcodegenerator

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareFileIdentityTest {
    @Test
    fun lanShareUsesTheActiveRouterSubnet() {
        val local = java.net.InetAddress.getByName("192.168.1.23") as java.net.Inet4Address
        val sameGatewaySubnet = java.net.InetAddress.getByName("192.168.1.99") as java.net.Inet4Address
        val otherGatewaySubnet = java.net.InetAddress.getByName("192.168.2.9") as java.net.Inet4Address
        assertEquals(true, LanShareManager.areOnSameRouterSubnet(local, sameGatewaySubnet, 24))
        assertEquals(false, LanShareManager.areOnSameRouterSubnet(local, otherGatewaySubnet, 24))
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
