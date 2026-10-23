package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareClientAuthTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun AndroidClientListsFilesOnlyWithTheSessionToken() {
        val server = LanShareServer(
            "127.0.0.1", 0, temporaryFolder.newFolder(), AppLogger { _, _, _ -> },
        )
        val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })
        try {
            server.start(5_000, false)
            val baseUrl = "http://127.0.0.1:${server.listeningPort}"

            assertTrue(client.list(LanShareSession(baseUrl, server.sessionToken)).isEmpty())
            val unauthorized = runCatching { client.list(LanShareSession(baseUrl)) }.exceptionOrNull()
            assertTrue("tokenless Android client must be denied", unauthorized != null)
        } finally {
            server.stop()
        }
    }

    @Test
    fun downloadReplacesExistingDestinationAfterCompleteResponse() {
        val sharedFolder = temporaryFolder.newFolder("shared")
        File(sharedFolder, "remote.bin").writeText("downloaded bytes")
        val destination = temporaryFolder.newFile("destination.bin").apply { writeText("previous bytes") }
        val server = LanShareServer("127.0.0.1", 0, sharedFolder, AppLogger { _, _, _ -> })
        val client = LanShareClient(isRouterLanHost = { true }, logger = AppLogger { _, _, _ -> })

        try {
            server.start(5_000, false)
            client.downloadToFile(
                LanShareSession("http://127.0.0.1:${server.listeningPort}", server.sessionToken),
                "remote.bin",
                destination,
            )

            assertEquals("downloaded bytes", destination.readText())
        } finally {
            server.stop()
        }
    }

    @Test
    fun uploadRejectsHostOutsideRouterSubnetBeforeOpeningFileSource() {
        var sourceOpened = false
        val client = LanShareClient(isRouterLanHost = { false }, logger = AppLogger { _, _, _ -> })
        val source = LanShareUploadSource(
            name = "private.bin",
            size = 1,
            mimeType = "application/octet-stream",
        ) {
            sourceOpened = true
            ByteArrayInputStream(byteArrayOf(1))
        }

        val failure = runCatching {
            client.upload(
                LanShareSession("http://192.0.2.1:18181", "test-token"),
                "invalid-host-test",
                source,
            ) { _, _ -> }
        }.exceptionOrNull()

        assertTrue("out-of-subnet upload must be rejected", failure is IllegalArgumentException)
        assertEquals("分享地址不在当前路由器网关子网内", failure?.message)
        assertFalse("rejected upload must not open the selected file", sourceOpened)
    }
}
