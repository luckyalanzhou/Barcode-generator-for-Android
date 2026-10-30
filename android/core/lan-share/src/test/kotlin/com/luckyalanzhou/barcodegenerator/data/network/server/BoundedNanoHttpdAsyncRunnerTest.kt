package com.luckyalanzhou.barcodegenerator.data.network.server

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.SocketException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoundedNanoHttpdAsyncRunnerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun excessHandlersAreClosedAndCapacityReturnsAfterHandlerFinishes() {
        val owner = TestNanoHttpd()
        val runner = BoundedNanoHttpdAsyncRunner(maxConcurrentConnections = 1)
        val running = CountDownLatch(1)
        val release = CountDownLatch(1)
        val rejectedClosed = CountDownLatch(1)
        try {
            runner.exec(owner.handler(onRun = {
                running.countDown()
                release.await(3, TimeUnit.SECONDS)
            }))
            assertTrue("first handler did not start", running.await(1, TimeUnit.SECONDS))

            runner.exec(owner.handler(onClose = rejectedClosed::countDown))
            assertTrue("handler over the configured limit was not closed", rejectedClosed.await(1, TimeUnit.SECONDS))
            assertEquals(1, runner.activeHandlerCount)

            release.countDown()
            val finishDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (runner.activeHandlerCount != 0 && System.nanoTime() < finishDeadline) Thread.sleep(5)
            assertEquals(0, runner.activeHandlerCount)

            val nextStarted = CountDownLatch(1)
            runner.exec(owner.handler(onRun = nextStarted::countDown))
            assertTrue("released capacity was not reusable", nextStarted.await(1, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            runner.closeAll()
            owner.stop()
        }
    }

    @Test
    fun serverClosesConnectionsAboveConfiguredClientHandlerLimit() {
        val server = LanShareServer(
            host = "127.0.0.1",
            port = 0,
            folder = temporaryFolder.newFolder(),
            logger = AppLogger { _, _, _ -> },
            maxConcurrentClientConnections = 1,
        )
        var heldConnection: Socket? = null
        var rejectedConnection: Socket? = null
        try {
            server.start(3_000, false)
            heldConnection = Socket("127.0.0.1", server.listeningPort)
            val activeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (server.activeClientHandlerCount == 0 && System.nanoTime() < activeDeadline) Thread.sleep(5)
            assertEquals(1, server.activeClientHandlerCount)

            rejectedConnection = Socket("127.0.0.1", server.listeningPort).apply { soTimeout = 1_000 }
            val rejectedRead = try {
                rejectedConnection.getInputStream().read()
            } catch (_: SocketException) {
                // Windows may report an immediate server-side close as TCP reset instead of EOF.
                -1
            }
            assertEquals(-1, rejectedRead)
            assertEquals(1, server.activeClientHandlerCount)

            heldConnection.close()
            val releaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (server.activeClientHandlerCount != 0 && System.nanoTime() < releaseDeadline) Thread.sleep(5)
            assertEquals(0, server.activeClientHandlerCount)

            val connection = URL("http://127.0.0.1:${server.listeningPort}/").openConnection() as HttpURLConnection
            try {
                assertEquals(200, connection.responseCode)
            } finally {
                connection.disconnect()
            }
        } finally {
            heldConnection?.close()
            rejectedConnection?.close()
            server.stop()
        }
    }

    private class TestNanoHttpd : NanoHTTPD(0) {
        private val createdSockets = mutableListOf<Socket>()

        override fun serve(session: IHTTPSession): Response =
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")

        fun handler(onRun: () -> Unit = {}, onClose: () -> Unit = {}): ClientHandler {
            val socket = Socket()
            createdSockets += socket
            return object : ClientHandler(ByteArrayInputStream(ByteArray(0)), socket) {
                override fun run() = onRun()
                override fun close() {
                    onClose()
                    socket.close()
                }
            }
        }

        override fun stop() {
            createdSockets.forEach { runCatching(it::close) }
            super.stop()
        }
    }
}
