package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
        val unvalidatedTarget = ServerSocket(0, 1, InetAddress.getByName("127.0.0.2")).apply { soTimeout = 5_000 }
        val initialRequestReceived = AtomicBoolean(false)
        val targetReceivedRequest = AtomicBoolean(false)
        val targetClosedForCleanup = AtomicBoolean(false)
        val redirectClosedForCleanup = AtomicBoolean(false)
        val redirectSocket = AtomicReference<java.net.Socket?>()
        var redirectServerFailure: Throwable? = null
        val targetThread = Thread({
            try {
                unvalidatedTarget.accept().use { socket ->
                    targetReceivedRequest.set(true)
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                        flush()
                    }
                }
            } catch (closed: SocketException) {
                if (!targetClosedForCleanup.get()) throw closed
            }
        }, "unvalidated-lan-redirect-target").apply {
            isDaemon = true
            start()
        }
        val redirectThread = Thread({
            try {
                redirectServer.accept().use { socket ->
                    redirectSocket.set(socket)
                    initialRequestReceived.set(true)
                    val request = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = request.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val response =
                        "HTTP/1.1 302 Found\r\n" +
                            "Location: http://127.0.0.2:${unvalidatedTarget.localPort}/api/files\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: keep-alive\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(response.toByteArray(StandardCharsets.US_ASCII))
                        flush()
                    }
                    // 等 HttpURLConnection 读完整个响应后再关闭测试夹具。
                    socket.getInputStream().read()
                }
            } catch (failure: Throwable) {
                if (!redirectClosedForCleanup.get() || failure !is SocketException) {
                    redirectServerFailure = failure
                }
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
            targetClosedForCleanup.set(true)
            unvalidatedTarget.close()
            redirectClosedForCleanup.set(true)
            redirectSocket.get()?.close()
            redirectThread.join(5_000)
            targetThread.join(5_000)

            assertFalse("redirect server must finish", redirectThread.isAlive)
            assertFalse("redirect target listener must finish", targetThread.isAlive)
            assertNull("redirect server must not fail", redirectServerFailure)
            assertTrue("client must reach the validated initial host", initialRequestReceived.get())
            assertFalse("client must not follow to a host rejected by subnet validation", targetReceivedRequest.get())
            assertNotNull("302 response must be surfaced as a request failure", failure)
            assertTrue(
                "redirect response status must be reported; actual failure=$failure",
                failure?.message.orEmpty().contains("302"),
            )
        } finally {
            redirectServer.close()
            targetClosedForCleanup.set(true)
            unvalidatedTarget.close()
        }
    }
}
