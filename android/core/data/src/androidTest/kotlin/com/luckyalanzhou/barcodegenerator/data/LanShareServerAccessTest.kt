package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanShareServerAccessTest {
    @Test
    fun protectsListingUploadAndWebSocketButAllowsManualCodeEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(context.cacheDir, "lan-share-access-test-${System.nanoTime()}").apply { mkdirs() }
        val token = "0123456789abcdefghijAB"
        val server = LanShareServer("127.0.0.1", 0, folder, token, "A7B2", AppLogger { _, _, _ -> })
        try {
            server.start(5_000, false)
            val base = "http://127.0.0.1:${server.listeningPort}"
            assertEquals(403, request("$base/api/files").first)
            assertEquals(403, request("$base/api/files?token=wrong").first)
            assertEquals(200, request("$base/api/files?token=$token").first)
            assertEquals(200, request("$base/api/files", token).first)
            assertEquals(403, request("$base/upload", method = "PUT").first)
            assertEquals(403, request("$base/ws", websocket = true).first)
            val entry = request(base)
            assertEquals(200, entry.first)
            assertTrue(entry.second.contains("访问码"))
            assertEquals(403, postCode("$base/join", "wrong").first)
            assertEquals(303 to "/?token=$token", postCode("$base/join", "a7b2"))
            val share = request("$base/?token=$token")
            assertEquals(200, share.first)
            assertTrue(share.second.contains("/api/files"))
        } finally {
            server.stop()
            folder.delete()
        }
    }

    private fun postCode(url: String, code: String): Pair<Int, String?> {
        val body = "code=$code".toByteArray(Charsets.UTF_8)
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setFixedLengthStreamingMode(body.size)
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
            connection.responseCode to connection.getHeaderField("Location")
        } finally {
            connection.disconnect()
        }
    }

    private fun request(url: String, token: String? = null, method: String = "GET", websocket: Boolean = false): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.requestMethod = method
            if (token != null) connection.setRequestProperty("X-Lan-Token", token)
            if (websocket) {
                connection.setRequestProperty("Upgrade", "websocket")
                connection.setRequestProperty("Connection", "Upgrade")
                connection.setRequestProperty("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
                connection.setRequestProperty("Sec-WebSocket-Version", "13")
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            status to body
        } finally {
            connection.disconnect()
        }
    }
}
