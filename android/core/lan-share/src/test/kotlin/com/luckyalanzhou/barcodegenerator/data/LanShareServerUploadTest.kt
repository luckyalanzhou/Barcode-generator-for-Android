package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
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
    ): Pair<Int, String> {
        val encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
        val connection = URL("http://127.0.0.1:$port/upload?name=$encodedName&client=$clientId").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.requestMethod = "PUT"
            connection.setRequestProperty("X-File-Size", declaredSize.toString())
            connection.setRequestProperty("Content-Type", "application/octet-stream")
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
