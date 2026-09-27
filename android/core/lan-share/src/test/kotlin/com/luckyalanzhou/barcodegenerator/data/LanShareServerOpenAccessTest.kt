package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeEvent
import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanShareServerOpenAccessTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

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

}
