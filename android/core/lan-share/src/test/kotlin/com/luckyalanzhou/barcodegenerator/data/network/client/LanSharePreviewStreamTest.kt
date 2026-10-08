package com.luckyalanzhou.barcodegenerator.data.network.client

import java.io.InputStream
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class LanSharePreviewStreamTest {
    @Test
    fun acceptsPayloadAtPreviewLimit() {
        val input = SizedInputStream(MAX_LAN_SHARE_PREVIEW_BYTES)
        val output = CountingOutputStream()

        assertEquals(MAX_LAN_SHARE_PREVIEW_BYTES, copyLanSharePreview(input, output))
        assertEquals(MAX_LAN_SHARE_PREVIEW_BYTES, output.bytesWritten)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPayloadOverPreviewLimit() {
        copyLanSharePreview(SizedInputStream(MAX_LAN_SHARE_PREVIEW_BYTES + 1L), CountingOutputStream())
    }

    @Test
    fun acceptsLargerTiffPreviewWhenCallerRaisesTheBound() {
        val tiffLimit = 256L * 1024L * 1024L
        val testLimit = 24L
        assertEquals(testLimit, copyLanSharePreview(SizedInputStream(testLimit), CountingOutputStream(), tiffLimit))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTiffPreviewAboveItsRaisedBound() {
        val testLimit = 24L
        copyLanSharePreview(SizedInputStream(testLimit + 1L), CountingOutputStream(), testLimit)
    }

    private class SizedInputStream(totalBytes: Long) : InputStream() {
        private var remaining = totalBytes

        override fun read(): Int = if (remaining == 0L) -1 else {
            remaining--
            0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val count = minOf(length.toLong(), remaining).toInt()
            if (count == 0) return -1
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }
    }

    private class CountingOutputStream : OutputStream() {
        var bytesWritten = 0L
            private set

        override fun write(value: Int) {
            bytesWritten++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            bytesWritten += length
        }
    }
}
