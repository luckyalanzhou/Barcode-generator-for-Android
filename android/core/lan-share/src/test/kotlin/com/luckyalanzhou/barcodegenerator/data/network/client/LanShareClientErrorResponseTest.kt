package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
        val observed = withErrorResponseJustOverLimit { session ->
            runCatching { client.list(session) }.exceptionOrNull()
        }

        assertBoundedFailure(observed.result, observed.serverState)
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

    @Test
    fun errorBodyPreservesReceivedPrefixWhenConnectionResetsMidRead() {
        val prefix = "REMOTE_ERROR:partial body".toByteArray(Charsets.UTF_8)
        val detail = readLanShareErrorBody(ResetAfterPrefixInputStream(prefix))

        assertTrue("received error prefix must survive a socket reset", detail.startsWith("REMOTE_ERROR:partial"))
        assertTrue("interrupted body should be identified", detail.endsWith("…（错误详情读取中断）"))
    }

    private fun assertBoundedFailure(failure: Throwable?, serverState: String) {
        val message = failure?.message.orEmpty()
        assertTrue(
            "server error should be reported; actual message: $message; $serverState",
            message.contains("REMOTE_ERROR:"),
        )
        assertTrue("error detail must have a fixed upper bound, was ${message.length} characters; $serverState", message.length < 5_000)
    }

    private data class ErrorResponseObservation<T>(val result: T, val serverState: String)

    private fun <T> withErrorResponseJustOverLimit(request: (LanShareSession) -> T): ErrorResponseObservation<T> {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }
        val requestAccepted = AtomicBoolean(false)
        val responseWrittenAt = AtomicLong(0L)
        val allowServerClose = CountDownLatch(1)
        val handlerFinished = CountDownLatch(1)
        val serverFailure = AtomicReference<Throwable?>()
        val serverThread = Thread({
            try {
                server.accept().use { socket ->
                    val requestHeaders = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = requestHeaders.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    requestAccepted.set(true)
                    val errorPrefix = "REMOTE_ERROR:"
                    val responseBody =
                        (errorPrefix + "x".repeat(MAX_LAN_SHARE_ERROR_BODY_BYTES + 1 - errorPrefix.length))
                            .toByteArray(StandardCharsets.UTF_8)
                    val responseHeaders =
                        ("HTTP/1.1 500 Internal Server Error\r\n" +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Length: ${responseBody.size}\r\n" +
                            "Connection: close\r\n\r\n")
                            .toByteArray(StandardCharsets.US_ASCII)
                    socket.getOutputStream().apply {
                        write(responseHeaders)
                        write(responseBody)
                        flush()
                    }
                    responseWrittenAt.set(System.nanoTime())
                    // 等客户端完成响应读取后再关连接；HttpURLConnection 可能保留可复用连接而不发 EOF。
                    check(allowServerClose.await(5, TimeUnit.SECONDS)) { "client did not finish reading the error response" }
                }
            } catch (_: SocketTimeoutException) {
                if (!requestAccepted.get()) serverFailure.set(IllegalStateException("loopback client did not connect"))
            } catch (failure: SocketException) {
                if (responseWrittenAt.get() == 0L) serverFailure.set(failure)
            } catch (failure: Throwable) {
                serverFailure.set(failure)
            } finally {
                handlerFinished.countDown()
                server.close()
            }
        }, "lan-error-response-server").apply {
            isDaemon = true
            start()
        }
        try {
            val result = request(LanShareSession("http://127.0.0.1:${server.localPort}"))
            val requestReturnedAt = System.nanoTime()
            allowServerClose.countDown()
            check(handlerFinished.await(5, TimeUnit.SECONDS)) { "loopback HTTP handler did not finish" }
            serverThread.join(5_000)
            check(!serverThread.isAlive) { "loopback HTTP server thread did not finish" }
            serverFailure.get()?.let { throw AssertionError("loopback HTTP handler failed", it) }
            val serverState =
                "accepted=${requestAccepted.get()} responseWritten=${responseWrittenAt.get() != 0L} " +
                    "responseBeforeClientReturn=${responseWrittenAt.get() in 1L..requestReturnedAt}"
            check(requestAccepted.get()) { "loopback server did not accept the request; $serverState" }
            check(responseWrittenAt.get() != 0L) { "loopback server did not write the response; $serverState" }
            return ErrorResponseObservation(result, serverState)
        } finally {
            allowServerClose.countDown()
            server.close()
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

    private class ResetAfterPrefixInputStream(private val prefix: ByteArray) : InputStream() {
        private var delivered = false

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (delivered) throw java.net.SocketException("Connection reset")
            delivered = true
            prefix.copyInto(buffer, offset)
            return prefix.size
        }

        override fun read(): Int = throw java.net.SocketException("Connection reset")

        override fun close() {
            throw java.net.SocketException("Connection reset")
        }
    }
}
