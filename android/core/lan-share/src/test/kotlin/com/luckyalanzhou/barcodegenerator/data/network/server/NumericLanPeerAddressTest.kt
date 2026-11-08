package com.luckyalanzhou.barcodegenerator.data.network.server

import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NumericLanPeerAddressTest {
    @Test fun untrustedPeerHostnameIsReplacedWithNumericIpv4WithoutChangingAddress() {
        val original = InetAddress.getByAddress("unresolvable.invalid", byteArrayOf(10, 0, 2, 16))
        val numeric = numericLanPeerAddress(original)
        assertEquals("10.0.2.16", numeric.hostName)
        assertArrayEquals(original.address, numeric.address)
    }

    @Test fun ipv6PeerAlsoKeepsItsAddressAndUsesNumericHostname() {
        val original = InetAddress.getByAddress("unresolvable.invalid", ByteArray(16).apply { this[15] = 1 })
        val numeric = numericLanPeerAddress(original)
        assertEquals(original.hostAddress, numeric.hostName)
        assertArrayEquals(original.address, numeric.address)
    }
}
