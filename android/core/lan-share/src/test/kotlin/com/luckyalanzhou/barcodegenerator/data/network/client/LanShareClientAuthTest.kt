package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
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
}
