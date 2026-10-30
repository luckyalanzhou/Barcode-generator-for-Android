package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareClientUploadReceiptTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })

    @Test
    fun uploadAcceptsReceiptAtConfiguredByteLimit() {
        val receipt = "x".repeat(MAX_LAN_SHARE_UPLOAD_RECEIPT_BYTES)

        assertEquals(
            receipt,
            readLanShareUploadReceipt(ByteArrayInputStream(receipt.toByteArray(StandardCharsets.UTF_8))),
        )
    }

    @Test
    fun uploadRejectsReceiptOneByteOverLimit() {
        val receipt = "x".repeat(MAX_LAN_SHARE_UPLOAD_RECEIPT_BYTES + 1)

        val failure = runCatching {
            readLanShareUploadReceipt(ByteArrayInputStream(receipt.toByteArray(StandardCharsets.UTF_8)))
        }.exceptionOrNull()

        assertTrue("oversized success receipt must fail with an I/O error", failure is IOException)
        assertEquals("上传完成但文件标识过长", failure?.message)
    }

    @Test
    fun uploadUsesServerReceiptAndStoresExactPayload() {
        val sharedFolder = temporaryFolder.newFolder("shared")
        val payload = "test payload".toByteArray(StandardCharsets.UTF_8)
        val server = LanShareServer("127.0.0.1", 0, sharedFolder, AppLogger { _, _, _ -> })

        try {
            server.start(5_000, false)
            val session = LanShareSession("http://127.0.0.1:${server.listeningPort}", server.sessionToken)
            val receipt = client.upload(session, "receipt-limit-test", uploadSource(payload)) { _, _ -> }

            assertTrue("server receipt must identify a committed file", receipt.isNotBlank())
            assertArrayEquals(payload, sharedFolder.resolve(receipt).readBytes())
            assertEquals("only the completed file should be published", 1, sharedFolder.listFiles().orEmpty().size)
        } finally {
            server.stop()
        }
    }

    @Test
    fun loggerFailureDoesNotDiscardSuccessfulUploadReceipt() {
        val sharedFolder = temporaryFolder.newFolder("logger-failure-shared")
        val payload = "uploaded despite diagnostic failure".toByteArray(StandardCharsets.UTF_8)
        val server = LanShareServer("127.0.0.1", 0, sharedFolder, AppLogger { _, _, _ -> })
        val throwingLoggerClient = LanShareClient(
            isRouterLanHost = { true },
            logger = AppLogger { _, _, _ -> throw IOException("diagnostic sink unavailable") },
        )

        try {
            server.start(5_000, false)
            val session = LanShareSession("http://127.0.0.1:${server.listeningPort}", server.sessionToken)

            val receipt = throwingLoggerClient.upload(session, "logger-failure-upload", uploadSource(payload)) { _, _ -> }

            assertTrue("server receipt must be returned after a committed upload", receipt.isNotBlank())
            assertArrayEquals(payload, sharedFolder.resolve(receipt).readBytes())
        } finally {
            server.stop()
        }
    }

    private fun uploadSource(payload: ByteArray) = LanShareUploadSource(
        name = "payload.bin",
        size = payload.size.toLong(),
        mimeType = "application/octet-stream",
    ) { ByteArrayInputStream(payload) }
}
