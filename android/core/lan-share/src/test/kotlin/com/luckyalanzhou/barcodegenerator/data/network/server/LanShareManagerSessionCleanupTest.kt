package com.luckyalanzhou.barcodegenerator.data.network.server

import android.content.ContextWrapper
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class LanShareManagerSessionCleanupTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun failedPreviousRoomCleanupPreventsGatewayFromStartingNewRoom() {
        val filesRoot = temporaryFolder.newFolder("files")
        val cacheRoot = temporaryFolder.newFolder("cache")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = filesRoot
            override fun getCacheDir(): File = cacheRoot
        }
        val sharedFolder = File(filesRoot, "lan-share").apply { mkdirs() }
        val previousRoomFile = File(sharedFolder, "previous-room.txt").apply {
            writeText("private previous session")
        }
        val loopback = InetAddress.getByName("127.0.0.1") as Inet4Address
        val gateway: LanShareGateway = LanShareManager(
            context = context,
            logger = AppLogger { _, message, _ ->
                if (message.startsWith("previous session files")) {
                    throw IOException("diagnostic sink unavailable")
                }
            },
            isRouterLanHost = { host -> host == loopback.hostAddress },
            localIpv4Address = { loopback },
            deleteSharedFile = { false },
        )

        try {
            val failure = try {
                gateway.start()
                null
            } catch (error: IllegalStateException) {
                error
            }

            assertNotNull("清理失败时不能向新设备开放旧房间目录", failure)
            assertEquals("无法清理上次局域网分享文件，请重试", failure?.message)
            assertTrue("清理失败时保留原文件", previousRoomFile.exists())
            assertFalse("清理失败后不能存在活动浏览器连接", gateway.browserConnected())

            val messageFailure = try {
                gateway.sendLocalMessage("must not be accepted")
                null
            } catch (error: IllegalStateException) {
                error
            }
            assertNotNull("清理失败时 Gateway 不应持有已启动的分享服务", messageFailure)
        } finally {
            gateway.stop(clearSharedFiles = false)
        }
    }

    @Test
    fun loggerFailureAfterServerStartsDoesNotMakeStartFail() {
        val filesRoot = temporaryFolder.newFolder("started-files")
        val cacheRoot = temporaryFolder.newFolder("started-cache")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = filesRoot
            override fun getCacheDir(): File = cacheRoot
        }
        val loopback = InetAddress.getByName("127.0.0.1") as Inet4Address
        val gateway: LanShareGateway = LanShareManager(
            context = context,
            logger = AppLogger { _, message, _ ->
                if (message.startsWith("server started")) {
                    throw IOException("diagnostic sink unavailable")
                }
            },
            isRouterLanHost = { host -> host == loopback.hostAddress },
            localIpv4Address = { loopback },
            deleteSharedFile = { it.delete() },
        )

        try {
            val session = gateway.start()

            assertTrue("a successful server start must return its session", session.baseUrl.startsWith("http://127.0.0.1:"))
            gateway.sendLocalMessage("still running")
        } finally {
            gateway.stop(clearSharedFiles = false)
        }
    }
}
