package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
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
class LanShareClientUploadReceiptTest {
    private val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })

    @Test
    fun uploadAcceptsReceiptAtConfiguredByteLimit() {
        val receipt = "x".repeat(MAX_LAN_SHARE_UPLOAD_RECEIPT_BYTES)

        val (result, receivedBody) = withUploadResponse(receipt.toByteArray(StandardCharsets.UTF_8)) { session ->
            client.upload(session, "receipt-limit-test", uploadSource()) { _, _ -> }
        }

        assertEquals(receipt, result)
        assertEquals("test payload", receivedBody)
    }

    @Test
    fun uploadRejectsReceiptOneByteOverLimit() {
        val receipt = "x".repeat(MAX_LAN_SHARE_UPLOAD_RECEIPT_BYTES + 1)

        val (failure, receivedBody) = withUploadResponse(receipt.toByteArray(StandardCharsets.UTF_8)) { session ->
            runCatching { client.upload(session, "receipt-over-limit-test", uploadSource()) { _, _ -> } }
                .exceptionOrNull()
        }

        assertTrue("oversized success receipt must fail with an I/O error", failure is IOException)
        assertEquals("上传完成但文件标识过长", failure?.message)
        assertEquals("test payload", receivedBody)
    }

    private fun uploadSource(): LanShareUploadSource {
        val body = "test payload".toByteArray(StandardCharsets.UTF_8)
        return LanShareUploadSource(
            name = "payload.bin",
            size = body.size.toLong(),
            mimeType = "application/octet-stream",
        ) { ByteArrayInputStream(body) }
    }

    private fun <T> withUploadResponse(responseBody: ByteArray, request: (LanShareSession) -> T): Pair<T, String> =
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val serverTask = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream()
                        val headers = readRequestHeaders(input)
                        val contentLength = headers.lineSequence()
                            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                            ?.substringAfter(':')
                            ?.trim()
                            ?.toIntOrNull()
                            ?: error("upload request must declare Content-Length")
                        val uploadedBody = ByteArray(contentLength)
                        var offset = 0
                        while (offset < uploadedBody.size) {
                            val count = input.read(uploadedBody, offset, uploadedBody.size - offset)
                            check(count > 0) { "client closed before completing upload body" }
                            offset += count
                        }
                        val output = socket.getOutputStream()
                        output.write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Length: ${responseBody.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(StandardCharsets.US_ASCII),
                        )
                        output.write(responseBody)
                        output.flush()
                        String(uploadedBody, StandardCharsets.UTF_8)
                    }
                }
                val result = request(LanShareSession("http://127.0.0.1:${server.localPort}"))
                val receivedBody = serverTask.get(5, TimeUnit.SECONDS)
                result to receivedBody
            } finally {
                executor.shutdownNow()
            }
        }

    private fun readRequestHeaders(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        val delimiter = intArrayOf('\r'.code, '\n'.code, '\r'.code, '\n'.code)
        var matched = 0
        while (true) {
            val value = input.read()
            check(value >= 0) { "client closed before sending HTTP headers" }
            bytes.write(value)
            matched = when {
                value == delimiter[matched] -> matched + 1
                value == delimiter[0] -> 1
                else -> 0
            }
            if (matched == delimiter.size) {
                return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            }
        }
    }
}
