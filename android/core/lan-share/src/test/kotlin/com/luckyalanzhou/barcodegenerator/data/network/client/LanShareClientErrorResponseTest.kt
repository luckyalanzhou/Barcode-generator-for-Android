package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareClientErrorResponseTest {
    private val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })

    @Test
    fun listErrorResponseDoesNotCreateAnUnboundedExceptionMessage() {
        val failure = withLargeErrorResponse { session ->
            runCatching { client.list(session) }.exceptionOrNull()
        }

        assertBoundedFailure(failure)
    }

    @Test
    fun boundedErrorBodyReaderStopsAfterTheConfiguredLimit() {
        val input = CountingInputStream(
            ByteArrayInputStream(("REMOTE_ERROR:" + "x".repeat(64 * 1024)).toByteArray(Charsets.UTF_8)),
        )

        val detail = readLanShareErrorBody(input)

        assertTrue("useful error prefix should be preserved", detail.startsWith("REMOTE_ERROR:"))
        assertTrue("truncation should be visible", detail.endsWith("…（错误详情已截断）"))
        assertEquals(MAX_LAN_SHARE_ERROR_BODY_BYTES + 1, input.bytesRead)
        assertTrue("exception detail remains small", detail.length < 5_000)
    }

    private fun assertBoundedFailure(failure: Throwable?) {
        val message = failure?.message.orEmpty()
        assertTrue("server error should be reported; actual message: $message", message.contains("REMOTE_ERROR:"))
        assertTrue("error detail must have a fixed upper bound, was ${message.length} characters", message.length < 5_000)
    }

    private fun <T> withLargeErrorResponse(request: (LanShareSession) -> T): T {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }.use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val responseBody = "REMOTE_ERROR:" + "x".repeat(64 * 1024)
                val serverTask = executor.submit {
                    server.accept().use { socket ->
                        consumeRequestHeaders(socket.getInputStream())
                        val output = socket.getOutputStream()
                        val responseHeaders =
                            "HTTP/1.1 500 Internal Server Error\r\n" +
                                "Content-Length: ${responseBody.length}\r\n" +
                                "Connection: close\r\n\r\n"
                        output.write(responseHeaders.toByteArray(Charsets.ISO_8859_1))
                        output.write(responseBody.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                }
                val result = request(LanShareSession("http://127.0.0.1:${server.localPort}"))
                serverTask.get(5, TimeUnit.SECONDS)
                return result
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun consumeRequestHeaders(input: InputStream) {
        val delimiter = intArrayOf(13, 10, 13, 10)
        val window = IntArray(delimiter.size)
        var byteCount = 0
        while (true) {
            val value = input.read()
            check(value >= 0) { "client closed before sending HTTP headers" }
            window[byteCount % window.size] = value
            byteCount++
            if (byteCount >= delimiter.size && (0 until delimiter.size).all { offset ->
                    window[(byteCount - delimiter.size + offset) % window.size] == delimiter[offset]
                }
            ) {
                return
            }
        }
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var bytesRead = 0
            private set

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) bytesRead += count
            return count
        }

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) bytesRead++
            return value
        }
    }
}
