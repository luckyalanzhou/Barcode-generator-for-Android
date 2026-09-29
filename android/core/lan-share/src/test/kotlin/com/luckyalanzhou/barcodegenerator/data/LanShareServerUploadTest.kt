package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_SOCKET_READ_TIMEOUT_MS
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeEvent
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanShareServerUploadTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun fixedLengthUploadWritesOriginalBytesToSingleCommittedFile() {
        val folder = temporaryFolder.newFolder()
        val payload = ByteArray(768 * 1024 + 37) { index -> (index * 31).toByte() }
        val server = newServer(folder)
        try {
            server.start(5_000, false)
            val result = upload(server.listeningPort, payload, "原图 % sample.jpg", "app", chunked = false)

            assertEquals(200, result.first)
            assertTrue(result.second.startsWith("app_"))
            assertArrayEquals(payload, File(folder, result.second).readBytes())
            assertTrue(folder.listFiles().orEmpty().none { it.name.startsWith(".lan-upload-") || it.name.endsWith(".part") })
        } finally {
            server.stop()
        }
    }

    @Test
    fun chunkedBrowserUploadUsesDeclaredSizeAndPreservesBytes() {
        val folder = temporaryFolder.newFolder()
        val payload = ByteArray(384 * 1024 + 19) { index -> (index xor (index ushr 8)).toByte() }
        val server = newServer(folder)
        try {
            server.start(5_000, false)
            val result = upload(server.listeningPort, payload, "browser.bin", "cbrowser123", chunked = true)

            assertEquals(200, result.first)
            assertTrue(result.second.startsWith("web_"))
            assertTrue(result.second.contains("_cbrowser123_"))
            assertArrayEquals(payload, File(folder, result.second).readBytes())
        } finally {
            server.stop()
        }
    }

    @Test
    fun fixedLengthUploadSurvivesIdleGapLongerThanPreviousSocketTimeout() {
        val folder = temporaryFolder.newFolder()
        val firstPart = ByteArray(512 * 1024) { index -> (index * 13).toByte() }
        val secondPart = ByteArray(512 * 1024) { index -> (index * 29).toByte() }
        val server = newServer(folder)
        var connection: HttpURLConnection? = null
        try {
            server.start(LAN_SHARE_SOCKET_READ_TIMEOUT_MS, false)
            val uploadConnection = URL("http://127.0.0.1:${server.listeningPort}/upload?name=paused.bin&client=cbrowser123")
                .openConnection() as HttpURLConnection
            connection = uploadConnection
            uploadConnection.connectTimeout = 5_000
            uploadConnection.readTimeout = 20_000
            uploadConnection.requestMethod = "PUT"
            uploadConnection.setRequestProperty("X-File-Size", (firstPart.size + secondPart.size).toString())
            uploadConnection.setRequestProperty("Content-Type", "application/octet-stream")
            uploadConnection.doOutput = true
            uploadConnection.setFixedLengthStreamingMode(firstPart.size + secondPart.size)

            val output = uploadConnection.outputStream
            output.write(firstPart)
            output.flush()
            val stagingDeadline = System.nanoTime() + 5_000_000_000L
            while (folder.listFiles().orEmpty().none {
                    it.name.startsWith(".lan-upload-") && it.length() > 0L
                } && System.nanoTime() < stagingDeadline
            ) {
                Thread.sleep(10)
            }
            assertTrue(
                "server should persist upload bytes before the intentional idle gap",
                folder.listFiles().orEmpty().any { it.name.startsWith(".lan-upload-") && it.length() > 0L },
            )

            Thread.sleep(6_000)
            output.write(secondPart)
            output.close()

            val status = uploadConnection.responseCode
            val storedId = uploadConnection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText().trim() }
            assertEquals(200, status)
            assertArrayEquals(firstPart + secondPart, File(folder, storedId).readBytes())
        } finally {
            connection?.disconnect()
            server.stop()
        }
    }

    @Test
    fun abortedUploadRemovesTemporaryFileWithoutCommittingPartialBytes() {
        val folder = temporaryFolder.newFolder()
        val server = newServer(folder)
        var connection: HttpURLConnection? = null
        try {
            server.start(5_000, false)
            val partialPayload = ByteArray(256 * 1024) { index -> index.toByte() }
            val uploadConnection = URL("http://127.0.0.1:${server.listeningPort}/upload?name=partial.bin&client=cbrowser123")
                .openConnection() as HttpURLConnection
            connection = uploadConnection
            uploadConnection.connectTimeout = 5_000
            uploadConnection.readTimeout = 5_000
            uploadConnection.requestMethod = "PUT"
            uploadConnection.setRequestProperty("X-File-Size", (2 * 1024 * 1024).toString())
            uploadConnection.setRequestProperty("Content-Type", "application/octet-stream")
            uploadConnection.doOutput = true
            uploadConnection.setFixedLengthStreamingMode(2 * 1024 * 1024)
            val output = uploadConnection.outputStream
            output.write(partialPayload)
            output.flush()

            val stagedDeadline = System.nanoTime() + 5_000_000_000L
            while (folder.listFiles().orEmpty().none { it.name.startsWith(".lan-upload-") } && System.nanoTime() < stagedDeadline) {
                Thread.sleep(10)
            }
            assertTrue("server should stage the partial upload before it is cancelled", folder.listFiles().orEmpty().any {
                it.name.startsWith(".lan-upload-")
            })

            uploadConnection.disconnect()
            runCatching { output.close() }

            val cleanupDeadline = System.nanoTime() + 5_000_000_000L
            while (folder.listFiles().orEmpty().isNotEmpty() && System.nanoTime() < cleanupDeadline) {
                Thread.sleep(10)
            }
            assertTrue("cancelled upload must not leave staged or committed files", folder.listFiles().orEmpty().isEmpty())
        } finally {
            connection?.disconnect()
            server.stop()
        }
    }

    @Test
    fun downloadedFileUsesDlRouteAndPreservesOriginalBytes() {
        val folder = temporaryFolder.newFolder()
        val payload = "原始文件内容\r\nkey: value\n".toByteArray(StandardCharsets.UTF_8)
        val server = newServer(folder)
        try {
            server.start(5_000, false)
            val uploaded = upload(server.listeningPort, payload, "source.yaml", "cbrowser123", chunked = false)
            assertEquals(200, uploaded.first)

            val encodedId = URLEncoder.encode(uploaded.second, StandardCharsets.UTF_8.name())
            val connection = URL("http://127.0.0.1:${server.listeningPort}/dl/$encodedId")
                .openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                assertEquals(200, connection.responseCode)
                assertArrayEquals(payload, connection.inputStream.use { it.readBytes() })
            } finally {
                connection.disconnect()
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun committedBrowserUploadPreservesDeclaredMimeTypeInRealtimeEvent() {
        val folder = temporaryFolder.newFolder()
        val events = mutableListOf<LanShareRealtimeEvent>()
        val server = LanShareServer(
            "127.0.0.1", 0, folder, AppLogger { _, _, _ -> },
            emitRealtimeEvent = { events += it },
        )
        try {
            server.start(5_000, false)
            val uploaded = upload(
                server.listeningPort,
                byteArrayOf(1, 2, 3),
                "camera-photo",
                "cbrowser123",
                chunked = false,
                contentType = "image/heic",
            )

            assertEquals(200, uploaded.first)
            val event = events.filterIsInstance<LanShareRealtimeEvent.FileAdded>().single()
            assertEquals(uploaded.second, event.file.id)
            assertEquals("browser:cbrowser123", event.file.sender)
            assertEquals("image/heic", event.file.mimeType)
        } finally {
            server.stop()
        }
    }

    @Test
    fun mismatchedContentLengthAndDeclaredFileSizeAreRejectedBeforeSaving() {
        val folder = temporaryFolder.newFolder()
        val payload = byteArrayOf(1, 2, 3, 4)
        val server = newServer(folder)
        try {
            server.start(5_000, false)
            val result = upload(
                server.listeningPort,
                payload,
                "mismatch.bin",
                "cbrowser123",
                chunked = false,
                declaredSize = payload.size + 1L,
            )

            assertEquals(400, result.first)
            assertTrue(folder.listFiles().orEmpty().isEmpty())
        } finally {
            server.stop()
        }
    }

    private fun newServer(folder: File) = LanShareServer(
        "127.0.0.1",
        0,
        folder,
        AppLogger { _, _, _ -> },
    )

    private fun upload(
        port: Int,
        payload: ByteArray,
        fileName: String,
        clientId: String,
        chunked: Boolean,
        declaredSize: Long = payload.size.toLong(),
        contentType: String = "application/octet-stream",
    ): Pair<Int, String> {
        val encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
        val connection = URL("http://127.0.0.1:$port/upload?name=$encodedName&client=$clientId").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.requestMethod = "PUT"
            connection.setRequestProperty("X-File-Size", declaredSize.toString())
            connection.setRequestProperty("Content-Type", contentType)
            connection.doOutput = true
            if (chunked) connection.setChunkedStreamingMode(8 * 1024)
            else connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText().trim() }.orEmpty()
            status to response
        } finally {
            connection.disconnect()
        }
    }
}
