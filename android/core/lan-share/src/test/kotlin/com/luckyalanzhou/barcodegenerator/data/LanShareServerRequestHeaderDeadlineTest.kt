package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import java.net.Socket
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanShareServerRequestHeaderDeadlineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun slowlyDrippingRequestHeaderCannotHoldAHandlerPastItsTotalDeadline() {
        val server = LanShareServer(
            host = "127.0.0.1",
            port = 0,
            folder = temporaryFolder.newFolder(),
            logger = AppLogger { _, _, _ -> },
            httpRequestHeaderTimeoutMillis = HEADER_DEADLINE_MILLIS,
        )
        var socket: Socket? = null
        var writer: Thread? = null
        try {
            server.start(2_000, false)
            val clientSocket = Socket("127.0.0.1", server.listeningPort)
            socket = clientSocket
            val acceptedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (server.activeClientHandlerCount == 0 && System.nanoTime() < acceptedDeadline) {
                Thread.sleep(5)
            }
            assertEquals("server did not accept the test connection", 1, server.activeClientHandlerCount)

            writer = Thread({
                val partialRequest = (
                    "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Slow: " + "x".repeat(80) + "\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII)
                partialRequest.forEach { value ->
                    try {
                        clientSocket.getOutputStream().apply {
                            write(value.toInt())
                            flush()
                        }
                        Thread.sleep(DRIP_INTERVAL_MILLIS)
                    } catch (_: Exception) {
                        return@Thread
                    }
                }
            }, "slow-http-header-test-writer").apply { isDaemon = true; start() }

            val closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (server.activeClientHandlerCount != 0 && System.nanoTime() < closeDeadline) {
                Thread.sleep(5)
            }
            assertTrue(
                "slowly dripping HTTP headers kept a client handler past the total deadline",
                server.activeClientHandlerCount == 0,
            )
        } finally {
            socket?.close()
            writer?.join(1_000)
            server.stop()
        }
    }

    @Test
    fun keepAliveConnectionGetsANewTotalDeadlineForEveryRequestHeader() {
        val server = LanShareServer(
            host = "127.0.0.1",
            port = 0,
            folder = temporaryFolder.newFolder(),
            logger = AppLogger { _, _, _ -> },
            httpRequestHeaderTimeoutMillis = HEADER_DEADLINE_MILLIS,
        )
        var socket: Socket? = null
        var writer: Thread? = null
        try {
            server.start(2_000, false)
            val clientSocket = Socket("127.0.0.1", server.listeningPort).apply { soTimeout = 2_000 }
            socket = clientSocket
            clientSocket.getOutputStream().write(
                (
                    "GET /api/session?token=${server.sessionToken} HTTP/1.1\r\n" +
                        "Host: 127.0.0.1\r\nConnection: keep-alive\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII),
            )
            val responseHeaders = readResponseHeaders(clientSocket)
            assertTrue("first request did not complete successfully: $responseHeaders", responseHeaders.startsWith("HTTP/1.1 200"))
            assertTrue("test requires HTTP keep-alive", responseHeaders.contains("connection: keep-alive", ignoreCase = true))

            writer = startSlowHeaderWriter(
                clientSocket,
                "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Slow: ${"x".repeat(80)}\r\n\r\n",
            )
            val closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (server.activeClientHandlerCount != 0 && System.nanoTime() < closeDeadline) {
                Thread.sleep(5)
            }
            assertTrue(
                "keep-alive request headers were not given a fresh total deadline",
                server.activeClientHandlerCount == 0,
            )
        } finally {
            socket?.close()
            writer?.join(1_000)
            server.stop()
        }
    }

    private fun startSlowHeaderWriter(socket: Socket, request: String): Thread = Thread({
        request.toByteArray(Charsets.US_ASCII).forEach { value ->
            try {
                socket.getOutputStream().apply {
                    write(value.toInt())
                    flush()
                }
                Thread.sleep(DRIP_INTERVAL_MILLIS)
            } catch (_: Exception) {
                return@Thread
            }
        }
    }, "slow-http-header-test-writer").apply { isDaemon = true; start() }

    private fun readResponseHeaders(socket: Socket): String {
        val output = ByteArrayOutputStream()
        val input = socket.getInputStream()
        while (output.size() < MAX_RESPONSE_HEADER_BYTES) {
            val value = input.read()
            check(value >= 0) { "server closed before returning response headers" }
            output.write(value)
            if (output.toByteArray().toString(Charsets.US_ASCII).endsWith("\r\n\r\n")) {
                return output.toByteArray().toString(Charsets.US_ASCII)
            }
        }
        error("server response headers exceeded $MAX_RESPONSE_HEADER_BYTES bytes")
    }

    private companion object {
        const val HEADER_DEADLINE_MILLIS = 400L
        const val DRIP_INTERVAL_MILLIS = 60L
        const val MAX_RESPONSE_HEADER_BYTES = 8 * 1024
    }
}
