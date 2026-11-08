package com.luckyalanzhou.barcodegenerator.data.network.server

import android.content.ContextWrapper
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareManagerConcurrentStartProbeTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun stoppingAfterOverlappingStartsClosesEveryServer() {
        val filesRoot = temporaryFolder.newFolder("files")
        val cacheRoot = temporaryFolder.newFolder("cache")
        val sharedFolder = File(filesRoot, "lan-share").apply { mkdirs() }
        File(sharedFolder, "previous.txt").writeText("existing")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = filesRoot
            override fun getCacheDir(): File = cacheRoot
        }
        val loopback = InetAddress.getByName("127.0.0.1") as Inet4Address
        val bothStartsClearing = CountDownLatch(2)
        val manager = LanShareManager(
            context = context,
            logger = AppLogger { _, _, _ -> },
            isRouterLanHost = { host -> host == loopback.hostAddress },
            localIpv4Address = { loopback },
            deleteSharedFile = { file ->
                if (bothStartsClearing.count == 2L) {
                    bothStartsClearing.countDown()
                    // Let a second unguarded start reach clearFiles; a serialized
                    // manager proceeds after the bounded wait and the next call
                    // sees the already-cleared directory.
                    bothStartsClearing.await(500, TimeUnit.MILLISECONDS)
                } else bothStartsClearing.countDown()
                file.delete() || !file.exists()
            },
        )
        val executor = Executors.newFixedThreadPool(2)

        try {
            val sessions = (1..2).map {
                executor.submit<com.luckyalanzhou.barcodegenerator.domain.LanShareSession> {
                    manager.start()
                }
            }
            val ports = sessions.map { future ->
                val session = future.get(10, TimeUnit.SECONDS)
                session.baseUrl.substringAfterLast(':').toInt()
            }

            manager.stop(clearSharedFiles = false)

            val stillListening = ports.filter(::canConnect)
            assertFalse("stop must close both concurrent starts; leaked ports: $stillListening", stillListening.isNotEmpty())
        } finally {
            executor.shutdownNow()
            manager.stop(clearSharedFiles = false)
        }
    }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 250)
        }
    }.isSuccess
}
