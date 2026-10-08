package com.luckyalanzhou.barcodegenerator.data.network.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareUploadProgressTest {
    @Test
    fun copiesOriginalBytesAndReportsMonotonicProgressFromZeroToTotal() {
        val source = ByteArray(320 * 1024) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Pair<Long, Long>>()

        val copied = copyLanShareUpload(
            input = ByteArrayInputStream(source),
            output = output,
            totalBytes = source.size.toLong(),
        ) { uploaded, total -> progress += uploaded to total }

        assertEquals(source.size.toLong(), copied)
        assertTrue(source.contentEquals(output.toByteArray()))
        assertEquals(0L to source.size.toLong(), progress.first())
        assertEquals(source.size.toLong() to source.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (previous, current) -> current.first >= previous.first })
        assertTrue(progress.all { it.second == source.size.toLong() })
    }
}
