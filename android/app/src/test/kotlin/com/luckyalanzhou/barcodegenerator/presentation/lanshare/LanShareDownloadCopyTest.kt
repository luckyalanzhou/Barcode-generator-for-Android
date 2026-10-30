package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareDownloadCopyTest {
    @Test
    fun staleSessionDoesNotOpenSafDestination() {
        var openCount = 0

        val failure = runCatching {
            withActiveLanShareDownloadDestination(
                ensureActive = {},
                isSessionCurrent = { false },
            ) {
                openCount++
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(0, openCount)
    }

    @Test
    fun cancellationDuringCopyPreventsFurtherWritesToDestination() {
        var cancelled = false
        val input = object : InputStream() {
            private var reads = 0

            override fun read(): Int = -1

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads++
                buffer[offset] = if (reads == 1) 'A'.code.toByte() else 'B'.code.toByte()
                if (reads == 2) cancelled = true
                return 1
            }
        }
        val output = ByteArrayOutputStream()

        val failure = runCatching {
            copyLanShareDownloadToDestination(input, output) {
                if (cancelled) throw CancellationException("session closed")
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals("A", output.toString(Charsets.UTF_8.name()))
    }
}
