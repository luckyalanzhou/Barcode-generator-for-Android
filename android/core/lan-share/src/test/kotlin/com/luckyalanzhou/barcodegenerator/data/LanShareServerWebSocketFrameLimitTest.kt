package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.data.network.server.LAN_SHARE_WEBSOCKET_MAX_FRAME_BYTES
import com.luckyalanzhou.barcodegenerator.data.network.server.LAN_SHARE_WEBSOCKET_MAX_MESSAGE_BYTES
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import fi.iki.elonen.NanoWSD
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.rules.TemporaryFolder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareServerWebSocketFrameLimitTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun oversizedFrameHeaderIsRejectedBeforePayloadArrives() {
        val serverEvents = CopyOnWriteArrayList<String>()
        val server = newServer(serverEvents)
        var socket: Socket? = null
        try {
            server.start(2_000, false)
            socket = openWebSocket(server.listeningPort, server.sessionToken)

            writeFrameHeader(
                socket = socket,
                firstByte = 0x81,
                payloadLength = LAN_SHARE_WEBSOCKET_MAX_FRAME_BYTES.toLong() + 1,
            )

            assertServerRejectsBeforePayload(socket, serverEvents)
        } finally {
            socket?.close()
            server.stop()
        }
    }

    @Test
    fun frameBeyondNanoWsdIntegerBoundaryStillReceivesMessageTooBigClose() {
        val serverEvents = CopyOnWriteArrayList<String>()
        val server = newServer(serverEvents)
        var socket: Socket? = null
        try {
            server.start(2_000, false)
            socket = openWebSocket(server.listeningPort, server.sessionToken)
            writeFrameHeader(
                socket = socket,
                firstByte = 0x81,
                payloadLength = 3L * 1024 * 1024 * 1024,
            )

            assertServerClosesWithMessageTooBig(socket, serverEvents)
        } finally {
            socket?.close()
            server.stop()
        }
    }

    @Test
    fun fragmentedMessageCumulativeLimitIsCheckedBeforeNextPayloadArrives() {
        val serverEvents = CopyOnWriteArrayList<String>()
        val server = newServer(serverEvents)
        var socket: Socket? = null
        try {
            server.start(2_000, false)
            socket = openWebSocket(server.listeningPort, server.sessionToken)
            writeMaskedFrame(
                socket = socket,
                firstByte = 0x02,
                payload = ByteArray(FIRST_FRAGMENT_BYTES),
            )
            writeFrameHeader(
                socket = socket,
                firstByte = 0x80,
                payloadLength = (LAN_SHARE_WEBSOCKET_MAX_MESSAGE_BYTES - FIRST_FRAGMENT_BYTES + 1).toLong(),
            )
            assertServerRejectsBeforePayload(socket, serverEvents)
        } finally {
            socket?.close()
            server.stop()
        }
    }

    private fun newServer(serverEvents: MutableList<String> = mutableListOf()) = LanShareServer(
        host = "127.0.0.1",
        port = 0,
        folder = temporaryFolder.newFolder(),
        logger = AppLogger { tag, message, error -> serverEvents += "$tag: $message${error?.let { ": $it" }.orEmpty()}" },
        webSocketHeartbeatIntervalMs = 60_000,
        webSocketHeartbeatTimeoutMs = 120_000,
    )

    private fun openWebSocket(port: Int, token: String): Socket {
        val socket = Socket("127.0.0.1", port).apply { soTimeout = SOCKET_READ_TIMEOUT_MS }
        socket.getOutputStream().apply {
            write((
                "GET /ws?token=$token HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nUpgrade: websocket\r\n" +
                    "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                    "Sec-WebSocket-Version: 13\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII))
            flush()
        }
        val responseHeaders = ByteArrayOutputStream()
        val input = socket.getInputStream()
        while (responseHeaders.size() < 8_192) {
            responseHeaders.write(input.read().also { value ->
                check(value >= 0) { "server closed before completing WebSocket handshake" }
            })
            val headers = responseHeaders.toByteArray().toString(Charsets.US_ASCII)
            if (headers.endsWith("\r\n\r\n")) {
                check(headers.lineSequence().firstOrNull()?.contains(" 101 ") == true) {
                    "WebSocket handshake was rejected: ${headers.lineSequence().firstOrNull()}"
                }
                return socket
            }
        }
        error("WebSocket handshake response exceeded 8 KiB")
    }

    private fun writeMaskedFrame(socket: Socket, firstByte: Int, payload: ByteArray) {
        val output = socket.getOutputStream()
        writeFrameHeader(output, firstByte, payload.size.toLong())
        val mask = MASK_KEY
        val maskedPayload = ByteArray(payload.size) { index ->
            (payload[index].toInt() xor mask[index % mask.size].toInt()).toByte()
        }
        output.write(maskedPayload)
        output.flush()
    }

    private fun writeFrameHeader(socket: Socket, firstByte: Int, payloadLength: Long) =
        writeFrameHeader(socket.getOutputStream(), firstByte, payloadLength).also { socket.getOutputStream().flush() }

    private fun writeFrameHeader(output: java.io.OutputStream, firstByte: Int, payloadLength: Long) {
        require(payloadLength >= 0)
        output.write(firstByte)
        when {
            payloadLength < 126 -> output.write(0x80 or payloadLength.toInt())
            payloadLength <= 0xffff -> {
                output.write(0x80 or 126)
                output.write((payloadLength ushr 8).toInt())
                output.write(payloadLength.toInt())
            }
            else -> {
                output.write(0x80 or 127)
                DataOutputStream(output).writeLong(payloadLength)
            }
        }
        output.write(MASK_KEY)
        output.flush()
    }

    private fun assertServerClosesWithMessageTooBig(
        socket: Socket,
        serverEvents: List<String> = emptyList(),
        firstByteAlreadyRead: Int? = null,
    ) {
        val input = DataInputStream(socket.getInputStream())
        val firstByte = firstByteAlreadyRead ?: try {
            input.readUnsignedByte()
        } catch (timeout: SocketTimeoutException) {
            throw AssertionError("server waited for payload instead of rejecting the declared size", timeout)
        } catch (error: Exception) {
            throw AssertionError("server reset before sending MessageTooBig; server events=$serverEvents", error)
        }
        assertEquals("server should send a final unmasked Close frame", CLOSE_FRAME_HEADER, firstByte)
        val secondByte = try {
            input.readUnsignedByte()
        } catch (error: Exception) {
            throw AssertionError("server close frame was truncated; server events=$serverEvents", error)
        }
        assertEquals("server Close frames are unmasked", 0, secondByte and 0x80)
        val payloadLength = secondByte and 0x7f
        assertTrue("Close frame should include a status code", payloadLength >= 2)
        val payload = ByteArray(payloadLength)
        input.readFully(payload)
        val closeCode = ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff)
        assertEquals(NanoWSD.WebSocketFrame.CloseCode.MessageTooBig.getValue(), closeCode)
    }

    private fun assertServerRejectsBeforePayload(socket: Socket, serverEvents: List<String>) {
        val input = DataInputStream(socket.getInputStream())
        val firstByte = try {
            input.readUnsignedByte()
        } catch (timeout: SocketTimeoutException) {
            throw AssertionError("server waited for payload instead of rejecting the declared size", timeout)
        } catch (_: EOFException) {
            assertTrue("server closed without recording the size-limit rejection: $serverEvents",
                serverEvents.any { it.contains("MessageTooBig") })
            return
        } catch (_: SocketException) {
            // Windows 关闭未完成的客户端帧时可能返回 TCP reset；仍须确认服务端记录了明确的 1009 拒绝，
            // 证明它在收到后续 payload 前就停止读取。
            assertTrue("server reset without recording the size-limit rejection: $serverEvents",
                serverEvents.any { it.contains("MessageTooBig") })
            return
        }
        assertEquals("server should send a final unmasked Close frame", CLOSE_FRAME_HEADER, firstByte)
        assertServerClosesWithMessageTooBig(socket, serverEvents, firstByteAlreadyRead = firstByte)
    }

    private companion object {
        const val FIRST_FRAGMENT_BYTES = LAN_SHARE_WEBSOCKET_MAX_FRAME_BYTES * 3 / 4
        const val SOCKET_READ_TIMEOUT_MS = 1_000
        const val CLOSE_FRAME_HEADER = 0x88
        val MASK_KEY = byteArrayOf(0x13, 0x27, 0x41, 0x5b)
    }
}
