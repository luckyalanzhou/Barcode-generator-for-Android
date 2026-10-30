package com.luckyalanzhou.barcodegenerator.data.network.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareDownloadStreamTest {
    @Test
    fun acceptsDownloadAtConfiguredLimit() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val output = ByteArrayOutputStream()

        val copied = copyLanShareDownload(ByteArrayInputStream(bytes), output, maxBytes = bytes.size.toLong())

        assertEquals(bytes.size.toLong(), copied)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun rejectsUnknownLengthDownloadOverLimitBeforeWritingOverflowChunk() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        val output = ByteArrayOutputStream()

        val failure = runCatching {
            copyLanShareDownload(ByteArrayInputStream(bytes), output, maxBytes = 5L)
        }.exceptionOrNull()

        assertTrue("oversized stream must fail with an I/O error", failure is IOException)
        assertTrue("overflow chunk must not be written", output.size() <= 5)
    }
}
