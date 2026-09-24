package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanShareServerAuthTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun allEntryPointsRequireTokenBeforeFileAccessOrWebSocketUpgrade() {
        val token = "0123456789abcdefghijAB"
        val server = LanShareServer(
            "127.0.0.1", 0, temporaryFolder.newFolder(), token, AppLogger { _, _, _ -> },
        )
        try {
            server.start(5_000, false)
            val port = server.listeningPort
            val base = "http://127.0.0.1:$port"

            assertEquals(403, request("$base/api/files").first)
            assertEquals(403, request("$base/api/files?token=wrong").first)
            // Android 的 org.json 在本地 JVM 测试中是未实现的占位类；
            // 用不依赖 JSON 的心跳接口验证真实 HTTP 授权，文件列表由设备测试覆盖。
            assertEquals(200, request("$base/api/presence?token=$token").first)
            assertEquals(200, request("$base/api/presence", token).first)
            assertEquals(403, request("$base/upload", method = "PUT").first)
            assertEquals(403, request("$base/api/download/missing").first)
            assertTrue(request(base).second.contains("访问码"))
            assertTrue(request("$base/?token=$token").second.contains("/api/files"))

            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5_000
                socket.getOutputStream().write((
                    "GET /ws HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nUpgrade: websocket\r\n" +
                        "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII))
                assertTrue(socket.getInputStream().bufferedReader().readLine().contains("403"))
            }
        } finally {
            server.stop()
        }
    }

    private fun request(url: String, token: String? = null, method: String = "GET"): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.requestMethod = method
            if (token != null) connection.setRequestProperty("X-Lan-Token", token)
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            status to body
        } finally {
            connection.disconnect()
        }
    }
}
