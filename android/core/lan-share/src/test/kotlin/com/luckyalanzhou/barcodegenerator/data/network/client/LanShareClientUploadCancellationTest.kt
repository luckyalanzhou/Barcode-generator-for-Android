package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_STREAM_BUFFER_SIZE
import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareClientUploadCancellationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun cancellingActiveAppUploadDisconnectsRequestAndRemovesServerPartialFile() {
        val sharedFolder = temporaryFolder.newFolder("shared")
        val serverEvents = CopyOnWriteArrayList<String>()
        val server = LanShareServer(
            "127.0.0.1",
            0,
            sharedFolder,
            AppLogger { tag, message, error -> serverEvents += "$tag: $message${error?.let { ": $it" }.orEmpty()}" },
        )
        val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })
        val executor = Executors.newSingleThreadExecutor()
        val transferId = "cancel-${System.nanoTime()}"
        val fileSize = 4L * 1024L * 1024L
        val bytesBeforePause = LAN_SHARE_STREAM_BUFFER_SIZE * 4L
        val sourcePaused = CountDownLatch(1)
        val continueSource = CountDownLatch(1)
        var upload: java.util.concurrent.Future<String>? = null

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
                        if (bytesRead >= bytesBeforePause && bytesRead < fileSize) {
                            sourcePaused.countDown()
                            check(continueSource.await(8, TimeUnit.SECONDS)) { "test source was not released" }
                        }
                        if (bytesRead >= fileSize) return -1
                        val count = minOf(length.toLong(), fileSize - bytesRead).toInt()
                        buffer.fill(0x5a.toByte(), offset, offset + count)
                        bytesRead += count
                        return count
                    }
                }
            }
            val session = LanShareSession("http://127.0.0.1:${server.listeningPort}", server.sessionToken)
            upload = executor.submit<String> { client.upload(session, transferId, source) { _, _ -> } }

            assertTrue("upload source did not reach its pause point", sourcePaused.await(5, TimeUnit.SECONDS))
            assertTrue(
                "server should have staged the in-flight upload",
                awaitCondition { sharedFolder.listFiles().orEmpty().any { it.name.startsWith(".lan-upload-") } },
            )

            client.cancelUpload(transferId)
            continueSource.countDown()

            val uploadFailure = try {
                upload.get(5, TimeUnit.SECONDS)
                null
            } catch (error: ExecutionException) {
                error.cause
            }
            assertNotNull("cancelled upload must not report success", uploadFailure)
            assertTrue(
                "cancelled upload must remove staged and committed files; " +
                    "failure=$uploadFailure remaining=${sharedFolder.listFiles().orEmpty().map { it.name to it.length() }} " +
                    "serverEvents=$serverEvents activeHandlers=${server.activeClientHandlerCount}",
                awaitCondition { sharedFolder.listFiles().orEmpty().isEmpty() },
            )
        } finally {
            continueSource.countDown()
            client.cancelUpload(transferId)
            upload?.cancel(true)
            executor.shutdownNow()
            server.stop()
        }
    }

    private fun awaitCondition(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}
