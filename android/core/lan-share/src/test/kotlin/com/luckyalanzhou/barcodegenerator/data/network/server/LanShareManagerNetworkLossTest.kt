package com.luckyalanzhou.barcodegenerator.data.network.server

import android.content.ContextWrapper
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareManagerNetworkLossTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun losingWifiStopsBoundServerAndPublishesInvalidatedHostSession() {
        val filesRoot = temporaryFolder.newFolder("files")
        val cacheRoot = temporaryFolder.newFolder("cache")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = filesRoot
            override fun getCacheDir(): File = cacheRoot
        }
        val loopback = InetAddress.getByName("127.0.0.1") as Inet4Address
        var networkLost: (() -> Unit)? = null
        var unregisterCount = 0
        val manager = LanShareManager(
            context = context,
            logger = AppLogger { _, _, _ -> },
            isRouterLanHost = { host -> host == loopback.hostAddress },
            localIpv4Address = { loopback },
            deleteSharedFile = { it.delete() },
            registerWifiNetworkCallback = { onLost ->
                networkLost = onLost
                { unregisterCount++ }
            },
        )

        try {
            val session = manager.start()
            assertFalse(manager.observeRealtimeState().value.hostSessionInvalidated)

            networkLost!!.invoke()

            assertTrue(manager.observeRealtimeState().value.hostSessionInvalidated)
            assertFalse("the invalidated listener must not keep serving", manager.browserConnected())
            assertEquals("the network listener must be released", 1, unregisterCount)
            assertTrue("the closed server is no longer available for local messages", runCatching {
                manager.sendLocalMessage("after-loss")
            }.isFailure)
            assertTrue("the session had a valid local endpoint", session.baseUrl.startsWith("http://127.0.0.1:"))
        } finally {
            manager.stop(clearSharedFiles = false)
        }
    }
}
