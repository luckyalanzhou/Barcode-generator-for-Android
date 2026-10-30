package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareDownloadCancellation
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
    fun loggerFailureDoesNotReportCommittedDownloadAsFailed() {
        val sharedFolder = temporaryFolder.newFolder("logger-failure-shared")
        val payload = "downloaded despite diagnostic failure".toByteArray(StandardCharsets.UTF_8)
        File(sharedFolder, "source.bin").writeBytes(payload)
        val destination = temporaryFolder.newFile("download.bin")
        val server = LanShareServer("127.0.0.1", 0, sharedFolder, AppLogger { _, _, _ -> })
        val throwingLoggerClient = LanShareClient(
            isRouterLanHost = { true },
            logger = AppLogger { _, _, _ -> throw IOException("diagnostic sink unavailable") },
        )

        try {
            server.start(5_000, false)
            val session = LanShareSession("http://127.0.0.1:${server.listeningPort}", server.sessionToken)
            val remoteFile = server.filesSnapshot("peer").single()

            throwingLoggerClient.downloadToFile(session, remoteFile.id, destination)

            assertTrue("a successful download must replace its destination", destination.readBytes().contentEquals(payload))
        } finally {
            server.stop()
        }
    }

    @Test
    fun truncatedFixedLengthResponseFailsAndPreservesExistingDestination() {
        val destination = temporaryFolder.newFile("destination.bin").apply { writeText("previous bytes") }
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 300 }
        var serverFailure: Throwable? = null
        val serverThread = Thread({
            try {
                repeat(4) {
                    val accepted = try {
                        server.accept()
                    } catch (_: SocketTimeoutException) {
                        return@Thread
                    }
                    accepted.use { socket ->
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
                }
            } catch (failure: Throwable) {
                serverFailure = failure
            } finally {
                server.close()
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
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 300 }
        var serverFailure: Throwable? = null
        val serverThread = Thread({
            try {
                repeat(4) {
                    val accepted = try {
                        server.accept()
                    } catch (_: SocketTimeoutException) {
                        return@Thread
                    }
                    accepted.use { socket ->
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
                            // 固定长度响应只写出部分正文；半关闭输出，立即让客户端观察到 EOF。
                            socket.shutdownOutput()
                        }
                    }
                }
            } catch (failure: Throwable) {
                serverFailure = failure
            } finally {
                server.close()
            }
        }, "truncated-lan-preview-server").apply {
            isDaemon = true
            start()
        }
        val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })

        try {
            val startedAt = System.nanoTime()
            val failure = runCatching {
                client.downloadPreview(
                    LanShareSession("http://127.0.0.1:${server.localPort}"),
                    "preview.jpg",
                    destination,
                    maxBytes = 1_024L,
                )
            }.exceptionOrNull()
            val elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            serverThread.join(5_000)

            assertFalse("loopback response server must finish", serverThread.isAlive)
            assertNull("loopback response server must not fail", serverFailure)
            assertTrue("truncated preview must fail with an I/O error", failure is IOException)
            assertTrue("truncated preview should fail on EOF instead of waiting for read timeout; took ${elapsedMs}ms", elapsedMs < 10_000)
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
        val responseSent = AtomicBoolean(false)
        var serverFailure: Throwable? = null
        val serverThread = Thread({
            try {
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    socket.getOutputStream().apply {
                        write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Length: ${LanShareLimits.MAX_FILE_BYTES + 1L}\r\n" +
                                    "Connection: keep-alive\r\n\r\n"
                                ).toByteArray(StandardCharsets.US_ASCII),
                        )
                        flush()
                    }
                    responseSent.set(true)
                    // 等待客户端拒绝声明的文件大小并关闭连接。
                    socket.getInputStream().read()
                }
            } catch (failure: Throwable) {
                // HttpURLConnection 在读取响应正文前主动断开时，可能会重置套接字。
                if (!responseSent.get() || failure !is SocketException) serverFailure = failure
            } finally {
                server.close()
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

    @Test
    fun cancellingActiveDownloadDisconnectsSocketAndPreservesDestination() {
        val destination = temporaryFolder.newFile("destination.bin").apply { writeText("previous bytes") }
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }
        val responseStarted = CountDownLatch(1)
        val clientFinished = CountDownLatch(1)
        val cancellationRequested = AtomicBoolean(false)
        val clientFailure = AtomicReference<Throwable?>()
        val serverFailure = AtomicReference<Throwable?>()
        val serverThread = Thread({
            try {
                server.accept().use { socket ->
                    // Keep the server read timeout longer than the client assertion window.
                    // Otherwise a slow runner can report a timeout as if cancellation failed.
                    socket.soTimeout = 15_000
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    socket.getOutputStream().apply {
                        write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\nConnection: keep-alive\r\n\r\n"
                                .toByteArray(StandardCharsets.US_ASCII),
                        )
                        write(byteArrayOf(1, 2, 3))
                        flush()
                    }
                    responseStarted.countDown()
                    // 客户端不会发送请求正文；此处读到 EOF 表示下载套接字已关闭。
                    socket.getInputStream().read()
                }
            } catch (error: Throwable) {
                if (!cancellationRequested.get() || error !is SocketException) serverFailure.set(error)
            } finally {
                server.close()
            }
        }, "cancelled-lan-download-server").apply {
            isDaemon = true
            start()
        }
        val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })
        val cancellation = LanShareDownloadCancellation()
        Thread({
            try {
                client.downloadToFile(
                    LanShareSession("http://127.0.0.1:${server.localPort}"),
                    "remote.bin",
                    destination,
                    cancellation,
                )
            } catch (error: Throwable) {
                clientFailure.set(error)
            } finally {
                clientFinished.countDown()
            }
        }, "cancelled-lan-download-client").apply {
            isDaemon = true
            start()
        }

        try {
            assertTrue("server did not send the partial response", responseStarted.await(5, TimeUnit.SECONDS))
            cancellationRequested.set(true)
            cancellation.cancel()

            assertTrue("cancelled HTTP read did not stop", clientFinished.await(5, TimeUnit.SECONDS))
            serverThread.join(5_000)
            assertFalse("loopback response server must finish", serverThread.isAlive)
            assertNull("server must only observe the expected client disconnect", serverFailure.get())
            val downloadFailure = clientFailure.get()
            assertTrue(
                "socket cancellation should propagate as cancellation; actual=${downloadFailure?.javaClass?.name}: ${downloadFailure?.message}",
                downloadFailure is CancellationException,
            )
            assertEquals("previous bytes", destination.readText())
            assertFalse(temporaryFolder.root.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally {
            cancellation.cancel()
            server.close()
        }
    }
}
