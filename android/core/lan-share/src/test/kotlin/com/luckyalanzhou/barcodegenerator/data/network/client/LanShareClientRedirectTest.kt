package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareClientRedirectTest {
    @Test
    fun requestDoesNotFollowRedirectToHostOutsideValidatedSubnet() {
        val redirectServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }
        val unvalidatedTarget = ServerSocket(0, 1, InetAddress.getByName("127.0.0.2")).apply { soTimeout = 1_000 }
        val targetReceivedRequest = AtomicBoolean(false)
        var redirectServerFailure: Throwable? = null
        val targetThread = Thread({
            try {
                unvalidatedTarget.accept().use { targetReceivedRequest.set(true) }
            } catch (_: SocketTimeoutException) {
                // No redirected request is the expected result.
            }
        }, "unvalidated-lan-redirect-target").apply {
            isDaemon = true
            start()
        }
        val redirectThread = Thread({
            try {
                redirectServer.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val response =
                        "HTTP/1.1 302 Found\r\n" +
                            "Location: http://127.0.0.2:${unvalidatedTarget.localPort}/api/files\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(response.toByteArray(StandardCharsets.US_ASCII))
                        flush()
                    }
                }
            } catch (failure: Throwable) {
                redirectServerFailure = failure
            }
        }, "lan-redirect-server").apply {
            isDaemon = true
            start()
        }
        val client = LanShareClient(
            isRouterLanHost = { it == "127.0.0.1" },
            logger = AppLogger { _, _, _ -> },
        )

        try {
            val failure = runCatching {
                client.list(LanShareSession("http://127.0.0.1:${redirectServer.localPort}", "session-token"))
            }.exceptionOrNull()
            redirectThread.join(5_000)
            targetThread.join(2_000)

            assertFalse("redirect server must finish", redirectThread.isAlive)
            assertFalse("redirect target listener must finish", targetThread.isAlive)
            assertNull("redirect server must not fail", redirectServerFailure)
            assertNotNull("302 response must be surfaced as a request failure", failure)
            assertTrue("redirect response status must be reported", failure?.message.orEmpty().contains("302"))
            assertFalse("client must not follow to a host rejected by subnet validation", targetReceivedRequest.get())
        } finally {
            redirectServer.close()
            unvalidatedTarget.close()
        }
    }
}
