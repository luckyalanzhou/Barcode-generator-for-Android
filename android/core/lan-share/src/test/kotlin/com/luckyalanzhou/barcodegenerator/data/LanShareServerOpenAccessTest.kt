package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeEvent
import java.io.DataInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [35])
class LanShareServerOpenAccessTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun briefWebSocketReconnectDoesNotReportDeviceDisconnected() {
        val events = CopyOnWriteArrayList<LanShareRealtimeEvent>()
        val server = LanShareServer(
            "127.0.0.1", 0, temporaryFolder.newFolder(), AppLogger { _, _, _ -> },
            emitRealtimeEvent = events::add,
            connectionDisconnectGraceMs = 1_500,
        )
        var first: Socket? = null
        var second: Socket? = null
        try {
            server.start(5_000, false)
            first = openWebSocket(server.listeningPort)
            assertTrue(awaitCondition(1_000) { server.browserConnected() })

            first.close()
            assertTrue(awaitCondition(1_000) { !server.browserConnected() })
            Thread.sleep(100)

            second = openWebSocket(server.listeningPort)
            assertTrue(awaitCondition(1_000) { server.browserConnected() })
            Thread.sleep(1_600)

            assertEquals(
                listOf(LanShareRealtimeEvent.ConnectionChanged(true)),
                events.filterIsInstance<LanShareRealtimeEvent.ConnectionChanged>(),
            )

            second.close()
            assertTrue(awaitCondition(2_500) {
                events.filterIsInstance<LanShareRealtimeEvent.ConnectionChanged>() == listOf(
                    LanShareRealtimeEvent.ConnectionChanged(true),
                    LanShareRealtimeEvent.ConnectionChanged(false),
                )
            })
        } finally {
            first?.close()
            second?.close()
            server.stop()
        }
    }

    @Test
    fun websocketHeartbeatKeepsResponsiveBrowserConnectedAndDropsMissingPongs() {
        val events = CopyOnWriteArrayList<LanShareRealtimeEvent>()
        val server = LanShareServer(
            "127.0.0.1", 0, temporaryFolder.newFolder(), AppLogger { _, _, _ -> },
            emitRealtimeEvent = events::add,
            connectionDisconnectGraceMs = 50,
            webSocketHeartbeatIntervalMs = 100,
            webSocketHeartbeatTimeoutMs = 500,
        )
        var socket: Socket? = null
        val running = AtomicBoolean(true)
        val respondToPings = AtomicBoolean(true)
        val pongCount = AtomicInteger()
        try {
            server.start(2_000, false)
            val browser = openWebSocket(server.listeningPort)
            socket = browser
            val heartbeatReader = Thread({
                val input = DataInputStream(browser.getInputStream())
                val output = browser.getOutputStream()
                while (running.get()) {
                    val firstByte = input.readUnsignedByte()
                    val secondByte = input.readUnsignedByte()
                    var payloadLength = secondByte and 0x7f
                    if (payloadLength == 126) {
                        payloadLength = input.readUnsignedShort()
                    } else if (payloadLength == 127) {
                        throw AssertionError("server heartbeat payload unexpectedly exceeds 64 KB")
                    }
                    val payload = ByteArray(payloadLength)
                    input.readFully(payload)
                    when (firstByte and 0x0f) {
                        0x9 -> if (respondToPings.get()) {
                            val mask = byteArrayOf(0x13, 0x27, 0x41, 0x5b)
                            output.write(0x8a)
                            output.write(0x80 or payload.size)
                            output.write(mask)
                            payload.forEachIndexed { index, byte ->
                                output.write((byte.toInt() xor mask[index % mask.size].toInt()) and 0xff)
                            }
                            output.flush()
                            pongCount.incrementAndGet()
                        }
                        0x8 -> return@Thread
                    }
                }
            }, "lan-share-test-websocket-heartbeat").apply {
                isDaemon = true
                start()
            }

            assertTrue(awaitCondition(2_000) { pongCount.get() >= 3 })
            assertTrue(server.browserConnected())
            assertEquals(
                listOf(LanShareRealtimeEvent.ConnectionChanged(true)),
                events.filterIsInstance<LanShareRealtimeEvent.ConnectionChanged>(),
            )

            respondToPings.set(false)
            assertTrue("a client that stops answering heartbeats must be removed", awaitCondition(2_000) {
                !server.browserConnected()
            })
            assertTrue(awaitCondition(1_000) {
                events.filterIsInstance<LanShareRealtimeEvent.ConnectionChanged>() == listOf(
                    LanShareRealtimeEvent.ConnectionChanged(true),
                    LanShareRealtimeEvent.ConnectionChanged(false),
                )
            })
        } finally {
            running.set(false)
            socket?.close()
            server.stop()
        }
    }

    @Test
    fun httpAndWebSocketEntryPointsWorkWithoutCredentials() {
        val events = mutableListOf<LanShareRealtimeEvent>()
        val server = LanShareServer(
            "127.0.0.1", 0, temporaryFolder.newFolder(), AppLogger { _, _, _ -> },
            emitRealtimeEvent = { event -> events.add(event) },
        )
        try {
            server.start(5_000, false)
            val port = server.listeningPort
            val base = "http://127.0.0.1:$port"

            assertEquals(404, request("$base/api/presence").first)
            assertEquals(404, request("$base/api/events").first)
            assertEquals(200, request(base).first)
            assertTrue(request(base).second.contains("id=\"files\""))
            assertEquals(404, request("$base/join").first)
            assertEquals(404, request("$base/dl/missing").first)
            assertEquals(404, request("$base/api/download/missing").first)
            assertEquals(404, request("$base/api/preview/missing").first)

            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5_000
                socket.getOutputStream().write((
                    "GET /ws HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nUpgrade: websocket\r\n" +
                        "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII))
                assertTrue(socket.getInputStream().bufferedReader().readLine().contains(" 101 "))
                assertTrue(events.contains(LanShareRealtimeEvent.ConnectionChanged(true)))
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun textMessagesStayInSessionMemoryWithoutCreatingFiles() {
        val folder = temporaryFolder.newFolder()
        val events = mutableListOf<LanShareRealtimeEvent>()
        val server = LanShareServer(
            "127.0.0.1", 0, folder, AppLogger { _, _, _ -> },
            emitRealtimeEvent = { events += it },
        )
        try {
            server.start(5_000, false)

            val browser = server.receiveBrowserMessage("cbrowser123", "原样 YAML: 名称: 示例\n数量: 2")
            val host = server.sendLocalMessage("App 主机消息")
            val messages = server.messagesSnapshot()

            assertEquals(2, messages.size)
            assertEquals("browser:cbrowser123", messages[0].sender)
            assertEquals("原样 YAML: 名称: 示例\n数量: 2", messages[0].text)
            assertEquals("app", messages[1].sender)
            assertEquals(host.id, messages.last().id)
            assertTrue(browser!!.createdAt < host.createdAt)
            assertTrue(events.contains(LanShareRealtimeEvent.MessageAdded(browser)))
            assertTrue(events.contains(LanShareRealtimeEvent.MessageAdded(host)))
            assertTrue(folder.listFiles().orEmpty().isEmpty())
        } finally {
            server.stop()
        }
    }

    @Test
    fun sessionSnapshotContainsCurrentFileAndMessageHistory() {
        val folder = temporaryFolder.newFolder()
        val server = LanShareServer(
            "127.0.0.1", 0, folder, AppLogger { _, _, _ -> },
        )
        try {
            server.start(5_000, false)
            server.receiveBrowserMessage("cbrowser123", "browser message")
            server.sendLocalMessage("app message")
            File(folder, "app_123_photo.jpg").writeBytes(byteArrayOf(1, 2, 3))

            val snapshot = server.sessionSnapshot()

            assertEquals("photo.jpg", snapshot.files.single().name)
            assertEquals(listOf("browser message", "app message"), snapshot.messages.map { it.text })
        } finally {
            server.stop()
        }
    }

    @Test
    fun blankAndOversizedChatMessagesAreRejected() {
        val server = LanShareServer(
            "127.0.0.1", 0, temporaryFolder.newFolder(), AppLogger { _, _, _ -> },
        )
        try {
            assertEquals(null, server.receiveBrowserMessage("cbrowser123", " \n "))
            assertEquals(null, server.receiveBrowserMessage("cbrowser123", "中".repeat(22_000)))
            assertTrue(server.messagesSnapshot().isEmpty())
        } finally {
            server.stop()
        }
    }

    private fun request(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            status to body
        } finally {
            connection.disconnect()
        }
    }

    private fun openWebSocket(port: Int): Socket {
        val socket = Socket("127.0.0.1", port).apply { soTimeout = 5_000 }
        socket.getOutputStream().write((
            "GET /ws HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nUpgrade: websocket\r\n" +
                "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII))
        val statusLine = socket.getInputStream().bufferedReader().readLine()
        assertTrue("WebSocket upgrade failed: $statusLine", statusLine.contains(" 101 "))
        return socket
    }

    @Test
    fun syncSnapshotIsDeliveredThroughPeerWriteQueue() {
        val diagnostics = CopyOnWriteArrayList<String>()
        val server = LanShareServer("127.0.0.1", 0, temporaryFolder.newFolder(), AppLogger { _, message, error -> diagnostics.add("$message $error") })
        try {
            server.start(5_000, false)
            Socket("127.0.0.1", server.listeningPort).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                output.write(("GET /ws HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\n" +
                    "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                    "Sec-WebSocket-Version: 13\r\n\r\n").toByteArray(Charsets.US_ASCII))
                output.flush()
                val input = DataInputStream(socket.getInputStream())
                val header = StringBuilder()
                while (!header.endsWith("\r\n\r\n")) {
                    header.append(input.readUnsignedByte().toChar())
                    assertTrue(header.length < 8_192)
                }
                assertTrue(header.contains(" 101 "))
                val mask = byteArrayOf(1, 2, 3, 4)
                output.write(0x81)
                output.write(0x84)
                output.write(mask)
                "sync".toByteArray().forEachIndexed { index, byte -> output.write(byte.toInt() xor mask[index].toInt()) }
                output.flush()
                val first = try { input.readUnsignedByte() } catch (error: java.io.IOException) {
                    throw AssertionError("Snapshot delivery failed: $diagnostics", error)
                }
                assertEquals(0x81, first)
                val size = input.readUnsignedByte()
                assertTrue(size < 126)
                val payload = ByteArray(size)
                input.readFully(payload)
                assertTrue(payload.toString(Charsets.UTF_8).contains("\"type\":\"snapshot\""))
            }
        } finally { server.stop() }
    }

    private fun awaitCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

}
