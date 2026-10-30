package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_STREAM_BUFFER_SIZE
import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareManager
import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareGatewayUploadCancellationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun cancellingThroughGatewayStopsReadingSourceAndRemovesServerPartialFile() {
        val sharedFolder = temporaryFolder.newFolder("shared")
        val server = LanShareServer("127.0.0.1", 0, sharedFolder, AppLogger { _, _, _ -> })
        val gateway: LanShareGateway = LanShareManager(
            RuntimeEnvironment.getApplication(),
            AppLogger { _, _, _ -> },
        ) { host -> host == "127.0.0.1" }
        val executor = Executors.newSingleThreadExecutor()
        val transferId = "gateway-cancel-${System.nanoTime()}"
        val sourceReadCount = AtomicInteger()
        val fileSize = LAN_SHARE_STREAM_BUFFER_SIZE * 4L

        try {
            server.start(5_000, false)
            val source = LanShareUploadSource(
                name = "large.bin",
                size = fileSize,
                mimeType = "application/octet-stream",
            ) {
                object : InputStream() {
                    private var bytesRead = 0L

                    override fun read(): Int {
                        val oneByte = ByteArray(1)
                        val count = read(oneByte, 0, 1)
                        return if (count < 0) -1 else oneByte[0].toInt() and 0xff
                    }

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        sourceReadCount.incrementAndGet()
                        if (bytesRead >= fileSize) return -1
                        val count = minOf(length.toLong(), fileSize - bytesRead).toInt()
                        buffer.fill(0x5a.toByte(), offset, offset + count)
                        bytesRead += count
                        return count
                    }
                }
            }
            val session = LanShareSession("http://127.0.0.1:${server.listeningPort}", server.sessionToken)
            val upload = executor.submit<String> {
                gateway.upload(session, transferId, source) { uploadedBytes, _ ->
                    if (uploadedBytes > 0L) gateway.cancelUpload(transferId)
                }
            }
            val failure = try {
                upload.get(5, TimeUnit.SECONDS)
                null
            } catch (error: ExecutionException) {
                error.cause
            }

            assertTrue("gateway cancellation should produce a cancellation result; failure=$failure", failure is CancellationException)
            assertEquals("the copy loop must stop before consuming another source buffer", 1, sourceReadCount.get())
            assertTrue(
                "cancelled upload must remove the server's partial file; " +
                    "remaining=${sharedFolder.listFiles().orEmpty().map { it.name to it.length() }}",
                awaitCondition { sharedFolder.listFiles().orEmpty().isEmpty() },
            )
        } finally {
            gateway.cancelUpload(transferId)
            executor.shutdownNow()
            server.stop()
        }
    }

    private fun awaitCondition(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}
