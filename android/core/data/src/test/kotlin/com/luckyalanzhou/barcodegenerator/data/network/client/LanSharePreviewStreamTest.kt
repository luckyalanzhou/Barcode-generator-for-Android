package com.luckyalanzhou.barcodegenerator.data.network.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class LanSharePreviewStreamTest {
    @Test
    fun acceptsPayloadAtPreviewLimit() {
        val input = ByteArrayInputStream(ByteArray(MAX_LAN_SHARE_PREVIEW_BYTES.toInt()))
        val output = ByteArrayOutputStream()

        assertEquals(MAX_LAN_SHARE_PREVIEW_BYTES, copyLanSharePreview(input, output))
        assertEquals(MAX_LAN_SHARE_PREVIEW_BYTES.toInt(), output.size())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPayloadOverPreviewLimit() {
        val input = ByteArrayInputStream(ByteArray(MAX_LAN_SHARE_PREVIEW_BYTES.toInt() + 1))
        copyLanSharePreview(input, ByteArrayOutputStream())
    }
}
