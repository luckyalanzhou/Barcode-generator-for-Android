package com.luckyalanzhou.barcodegenerator.data.platform

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.UpdateLookupResult
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUpdateCatalogConnectionTest {
    @Test
    fun realCheckMapsLoopbackHttpFailureToFailedResult() {
        LoopbackHttpServer().use { server ->
            val logEntries = mutableListOf<Triple<String, String, Throwable?>>()
            val gateway = gateway(server, AppLogger { tag, message, error ->
                logEntries += Triple(tag, message, error)
            })

            val result = runBlocking {
                val resultTask = async(Dispatchers.IO) { gateway.check() }
                assertTrue("connection factory was not called", server.connectionRequested.await(3, TimeUnit.SECONDS))
                assertTrue("loopback socket was not accepted", server.clientAccepted.await(3, TimeUnit.SECONDS))
                assertTrue("HTTP request headers were not received", server.requestReceived.await(3, TimeUnit.SECONDS))
                assertTrue("loopback response was not sent", server.responseSent.await(3, TimeUnit.SECONDS))
                resultTask.await()
            }

            assertEquals(UpdateLookupResult.Failed("GitHub HTTP 503"), result)
            assertEquals(1, logEntries.size)
            assertEquals("update", logEntries.single().first)
            assertEquals("check failed", logEntries.single().second)
            assertTrue(logEntries.single().third is IllegalStateException)
            assertTrue(server.requestLine.get().orEmpty().startsWith("GET /repos/"))
        }
    }

    @Test
    fun diagnosticLoggerFailureDoesNotReplaceLoopbackHttpFailure() {
        LoopbackHttpServer().use { server ->
            val gateway = gateway(server, AppLogger { _, _, _ ->
                throw IOException("diagnostic storage unavailable")
            })

            val result = runBlocking {
                val resultTask = async(Dispatchers.IO) { gateway.check() }
                assertTrue("connection factory was not called", server.connectionRequested.await(3, TimeUnit.SECONDS))
                assertTrue("loopback socket was not accepted", server.clientAccepted.await(3, TimeUnit.SECONDS))
                assertTrue("HTTP request headers were not received", server.requestReceived.await(3, TimeUnit.SECONDS))
                assertTrue("loopback response was not sent", server.responseSent.await(3, TimeUnit.SECONDS))
                resultTask.await()
            }

            assertEquals(UpdateLookupResult.Failed("GitHub HTTP 503"), result)
        }
    }

    @Test
    fun checkPropagatesCancellationWithoutLoggingItAsNetworkFailure() {
        val cancellation = CancellationException("update check cancelled")
        val logEntries = mutableListOf<Triple<String, String, Throwable?>>()
        val gateway = AndroidUpdateCatalogGateway(
            logger = AppLogger { tag, message, error -> logEntries += Triple(tag, message, error) },
            updateTagPrefix = "android-test-v",
            apkFilePrefix = "BarcodeGeneratorBeta",
            currentVersionName = "9.9.9",
            currentVersionCode = 1L,
            connectionFactory = UpdateCatalogConnectionFactory { throw cancellation },
        )

        val thrown = runBlocking { runCatching { gateway.check() }.exceptionOrNull() }

        assertTrue(thrown is CancellationException)
        assertEquals(cancellation.message, thrown?.message)
        assertTrue(logEntries.isEmpty())
    }

    @Test
    fun cancellationDuringLoopbackCheckDoesNotLogHttpFailure() = runBlocking {
        val releaseResponse = CountDownLatch(1)
        LoopbackHttpServer(responseGate = releaseResponse).use { server ->
            val logEntries = mutableListOf<Triple<String, String, Throwable?>>()
            val job = launch(Dispatchers.IO) { gateway(server, AppLogger { tag, message, error ->
                logEntries += Triple(tag, message, error)
            }).check() }

            try {
                assertTrue("connection factory was not called", server.connectionRequested.await(3, TimeUnit.SECONDS))
                assertTrue("loopback socket was not accepted", server.clientAccepted.await(3, TimeUnit.SECONDS))
                assertTrue("HTTP request headers were not received", server.requestReceived.await(3, TimeUnit.SECONDS))
                job.cancel()
            } finally {
                releaseResponse.countDown()
                job.cancel()
            }

            // 生产连接的读取上限为 8 秒；给阻塞式 HttpURLConnection 留出完整超时预算。
            withTimeout(12_000) { job.join() }
            assertTrue(job.isCancelled)
            assertTrue(logEntries.isEmpty())
        }
    }

    private fun gateway(server: LoopbackHttpServer, logger: AppLogger) = AndroidUpdateCatalogGateway(
        logger = logger,
        updateTagPrefix = "android-test-v",
        apkFilePrefix = "BarcodeGeneratorBeta",
        currentVersionName = "9.9.9",
        currentVersionCode = 1L,
        connectionFactory = UpdateCatalogConnectionFactory { originalUrl ->
            server.connectionRequested.countDown()
            URL("http://127.0.0.1:${server.port}${originalUrl.file}")
                .openConnection(Proxy.NO_PROXY) as HttpURLConnection
        },
    )

    private class LoopbackHttpServer(
        private val responseGate: CountDownLatch? = null,
    ) : AutoCloseable {
        val connectionRequested = CountDownLatch(1)
        val clientAccepted = CountDownLatch(1)
        val requestReceived = CountDownLatch(1)
        val responseSent = CountDownLatch(1)
        val requestLine = AtomicReference<String?>()
        private val failure = AtomicReference<Throwable?>()
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "update-catalog-loopback").apply { isDaemon = true }
        }
        private val server = HttpServer.create(
            InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
            0,
        ).apply {
            this.executor = this@LoopbackHttpServer.executor
            createContext("/") { exchange ->
                clientAccepted.countDown()
                try {
                    requestLine.set("${exchange.requestMethod} ${exchange.requestURI} ${exchange.protocol}")
                    requestReceived.countDown()
                    if (responseGate != null && !responseGate.await(3, TimeUnit.SECONDS)) {
                        throw IOException("timed out waiting to release loopback response")
                    }
                    val responseBody = "{}".toByteArray(Charsets.UTF_8)
                    exchange.sendResponseHeaders(503, responseBody.size.toLong())
                    exchange.responseBody.use { output ->
                        output.write(responseBody)
                        output.flush()
                    }
                    responseSent.countDown()
                } catch (error: Throwable) {
                    // 取消用例会主动断开客户端，响应写入失败属于预期；其他服务端错误供测试关闭时报告。
                    if (error !is IOException || responseGate == null) failure.set(error)
                    requestReceived.countDown()
                } finally {
                    exchange.close()
                }
            }
            start()
        }
        val port: Int = server.address.port

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
            assertTrue("loopback HTTP server thread did not finish", executor.awaitTermination(3_500, TimeUnit.MILLISECONDS))
            failure.get()?.let { throw AssertionError("loopback HTTP server failed", it) }
        }
    }
}
