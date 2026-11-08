package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareClientUnknownLengthUploadTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun unknownLengthStreamIsStagedThenUploadedWithActualSizeAndCleaned() {
        val sharedFolder = temporaryFolder.newFolder("shared")
        val stagingFolder = File(temporaryFolder.root, "staging")
        val server = LanShareServer(
            "127.0.0.1",
            0,
            sharedFolder,
            AppLogger { _, _, _ -> },
        )
        val client = LanShareClient(
            isRouterLanHost = { true },
            logger = AppLogger { _, _, _ -> },
            uploadStagingDirectory = stagingFolder,
        )
        val payload = "unknown-length-document-provider".toByteArray(StandardCharsets.UTF_8)
        val progressTotals = mutableListOf<Long>()

        try {
            server.start(5_000, false)
            val source = LanShareUploadSource(
                name = "unknown.txt",
                size = -1L,
                mimeType = "text/plain",
            ) { ByteArrayInputStream(payload) }
            val session = LanShareSession("http://127.0.0.1:${server.listeningPort}", server.sessionToken)

            val uploadedId = client.upload(session, "unknown-length-upload", source) { _, totalBytes ->
                progressTotals += totalBytes
            }

            assertNotNull(uploadedId)
            val uploadedFile = sharedFolder.listFiles().orEmpty().single { it.name.endsWith("_unknown.txt") }
            assertEquals(payload.size.toLong(), uploadedFile.length())
            assertArrayEquals(payload, uploadedFile.readBytes())
            assertTrue("staged source file must be removed", stagingFolder.listFiles().orEmpty().isEmpty())
            assertTrue("unknown staging size should resolve before transfer progress", progressTotals.contains(payload.size.toLong()))
        } finally {
            server.stop()
        }
    }
}
