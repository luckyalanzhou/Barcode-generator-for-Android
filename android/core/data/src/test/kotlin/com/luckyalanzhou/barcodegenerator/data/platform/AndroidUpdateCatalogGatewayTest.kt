package com.luckyalanzhou.barcodegenerator.data.platform

import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidUpdateCatalogGatewayTest {
    @Test
    fun updateCatalogResponseAtLimitIsAccepted() {
        val input = CountingInputStream(totalBytes = MAX_RESPONSE_BYTES)

        val response = readUpdateCatalogJson(input)

        assertEquals(0, response.length())
        assertEquals(MAX_RESPONSE_BYTES, input.bytesRead)
        assertTrue(input.closed)
    }

    @Test
    fun oversizedUpdateCatalogResponseStopsAtLimitPlusOneByteAndClosesStream() {
        val input = CountingInputStream(totalBytes = MAX_RESPONSE_BYTES + 2)

        try {
            readUpdateCatalogJson(input)
            fail("Expected oversized update catalog response to be rejected")
        } catch (expected: IOException) {
            assertEquals("更新信息过大，请稍后重试", expected.message)
        }

        assertEquals(MAX_RESPONSE_BYTES + 1, input.bytesRead)
        assertTrue(input.closed)
    }

    private class CountingInputStream(private val totalBytes: Int) : InputStream() {
        private val validJsonPrefix = "[]".toByteArray(Charsets.UTF_8)
        var bytesRead: Int = 0
            private set
        var closed: Boolean = false
            private set

        override fun read(): Int {
            if (bytesRead >= totalBytes) return -1
            val value = byteAt(bytesRead)
            bytesRead++
            return value.toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= totalBytes) return -1
            val count = minOf(length, totalBytes - bytesRead)
            repeat(count) { index -> buffer[offset + index] = byteAt(bytesRead + index) }
            bytesRead += count
            return count
        }

        override fun close() {
            closed = true
        }

        private fun byteAt(index: Int): Byte =
            validJsonPrefix.getOrNull(index) ?: ' '.code.toByte()
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = MAX_UPDATE_CATALOG_RESPONSE_BYTES
    }
}
