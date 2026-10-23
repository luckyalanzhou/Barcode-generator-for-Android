package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareClientDownloadTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun truncatedFixedLengthResponseFailsAndPreservesExistingDestination() {
        val destination = temporaryFolder.newFile("destination.bin").apply { writeText("previous bytes") }
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }
        var serverFailure: Throwable? = null
        val serverThread = Thread({
            try {
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    socket.getOutputStream().use { response ->
                        response.write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 32\r\nConnection: close\r\n\r\n"
                                .toByteArray(StandardCharsets.US_ASCII),
                        )
                        response.write("truncated".toByteArray(StandardCharsets.US_ASCII))
                        response.flush()
                    }
                }
            } catch (failure: Throwable) {
                serverFailure = failure
            }
        }, "truncated-lan-download-server").apply {
            isDaemon = true
            start()
        }
        val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })

        try {
            val failure = runCatching {
                client.downloadToFile(
                    LanShareSession("http://127.0.0.1:${server.localPort}"),
                    "remote.bin",
                    destination,
                )
            }.exceptionOrNull()
            serverThread.join(5_000)

            assertFalse("loopback response server must finish", serverThread.isAlive)
            assertNull("loopback response server must not fail", serverFailure)
            assertTrue("truncated fixed-length response must fail with an I/O error", failure is IOException)
            assertEquals("previous bytes", destination.readText())
            assertFalse(
                temporaryFolder.root.listFiles().orEmpty().any { it.name.endsWith(".part") },
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun truncatedPreviewResponseFailsAndPreservesExistingDestination() {
        val destination = temporaryFolder.newFile("preview.jpg").apply { writeText("previous preview") }
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }
        var serverFailure: Throwable? = null
        val serverThread = Thread({
            try {
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    socket.getOutputStream().use { response ->
                        response.write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 32\r\nConnection: close\r\n\r\n"
                                .toByteArray(StandardCharsets.US_ASCII),
                        )
                        response.write("truncated".toByteArray(StandardCharsets.US_ASCII))
                        response.flush()
                    }
                }
            } catch (failure: Throwable) {
                serverFailure = failure
            }
        }, "truncated-lan-preview-server").apply {
            isDaemon = true
            start()
        }
        val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })

        try {
            val failure = runCatching {
                client.downloadPreview(
                    LanShareSession("http://127.0.0.1:${server.localPort}"),
                    "preview.jpg",
                    destination,
                    maxBytes = 1_024L,
                )
            }.exceptionOrNull()
            serverThread.join(5_000)

            assertFalse("loopback response server must finish", serverThread.isAlive)
            assertNull("loopback response server must not fail", serverFailure)
            assertTrue("truncated preview must fail with an I/O error", failure is IOException)
            assertEquals("previous preview", destination.readText())
            assertFalse(
                temporaryFolder.root.listFiles().orEmpty().any { it.name.endsWith(".part") },
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun declaredDownloadAboveProtocolLimitIsRejectedBeforeCreatingTemporaryFile() {
        val destination = temporaryFolder.newFile("destination.bin").apply { writeText("previous bytes") }
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }
        var serverFailure: Throwable? = null
        val serverThread = Thread({
            try {
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    socket.getOutputStream().use { response ->
                        response.write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Length: ${LanShareLimits.MAX_FILE_BYTES + 1L}\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(StandardCharsets.US_ASCII),
                        )
                        response.flush()
                    }
                }
            } catch (failure: Throwable) {
                serverFailure = failure
            }
        }, "oversized-lan-download-server").apply {
            isDaemon = true
            start()
        }
        val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })

        try {
            val failure = runCatching {
                client.downloadToFile(
                    LanShareSession("http://127.0.0.1:${server.localPort}"),
                    "oversized.bin",
                    destination,
                )
            }.exceptionOrNull()
            serverThread.join(5_000)

            assertFalse("loopback response server must finish", serverThread.isAlive)
            assertNull("loopback response server must not fail", serverFailure)
            assertTrue("oversized declared download must fail", failure is IOException)
            assertEquals("下载文件超过 10 GiB 限制", failure?.message)
            assertEquals("previous bytes", destination.readText())
            assertFalse(temporaryFolder.root.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally {
            server.close()
        }
    }
}
