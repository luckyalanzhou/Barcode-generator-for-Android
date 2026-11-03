package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeState
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareViewModelStartupRealtimeTest {
    @Test
    fun closingWhileHostStartIsBlockedStopsTheLateStartedServer() =
        assertLateHostStartupIsRolledBack(expectedSession = null) { viewModel, _ -> viewModel.closeSession() }

    @Test
    fun joiningWhileHostStartIsBlockedKeepsTheJoinedSession() {
        val joinedSession = LanShareSession("http://192.168.1.3:8081", accessToken = "peer-token")
        assertLateHostStartupIsRolledBack(expectedSession = joinedSession) { viewModel, session ->
            viewModel.joinSession(session)
        }
    }

    private fun assertLateHostStartupIsRolledBack(
        expectedSession: LanShareSession?,
        transition: (LanShareViewModel, LanShareSession) -> Unit,
    ) {
        val session = LanShareSession("http://192.168.1.2:8080")
        val startEntered = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val startFinished = CountDownLatch(1)
        val isRunning = AtomicBoolean(false)
        val stopCount = AtomicInteger()
        val stopClearSharedFiles = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val bothStopsFinished = CountDownLatch(2)
        val realtimeState = MutableStateFlow(LanShareRealtimeState())
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, args ->
            when (method.name) {
                "isOnLocalNetwork" -> true
                "isRouterLanHost", "browserConnected" -> false
                "localFile" -> null
                "start" -> {
                    startEntered.countDown()
                    check(releaseStart.await(5, TimeUnit.SECONDS))
                    isRunning.set(true)
                    startFinished.countDown()
                    session
                }
                "stop" -> {
                    stopClearSharedFiles += args!![0] as Boolean
                    stopCount.incrementAndGet().takeIf { it <= 2 }?.let { bothStopsFinished.countDown() }
                    isRunning.set(false)
                    Unit
                }
                "localFiles" -> emptyList<LanShareFile>()
                "localMessages" -> emptyList<LanShareMessage>()
                "observeRealtimeState" -> realtimeState
                "list" -> emptyList<LanShareFile>()
                else -> error("Unexpected gateway operation: ${method.name}")
            }
        } as LanShareGateway
        val viewModel = LanShareViewModel(
            lanShareGateway = gateway,
            previewDecoder = object : LanShareBitmapPreviewDecoder {
                override val maxDecodeEdge = 1

                override suspend fun decode(file: File, maxEdge: Int, isTiff: Boolean): Bitmap? = null
            },
            appContext = RuntimeEnvironment.getApplication(),
            logger = AppLogger { _, _, _ -> },
        )

        try {
            viewModel.startHostSessionAsync {}
            assertTrue("host start should reach the blocking gateway call", startEntered.await(5, TimeUnit.SECONDS))

            transition(viewModel, expectedSession ?: session)
            releaseStart.countDown()
            assertTrue("blocked host start should finish", startFinished.await(5, TimeUnit.SECONDS))
            assertTrue("the close and late-start rollback stops should finish", bothStopsFinished.await(5, TimeUnit.SECONDS))
            pumpMainUntilStartupSettles(viewModel)

            assertEquals("the selected session must survive a late host start", expectedSession, viewModel.uiState.value.session)
            assertFalse("closing during startup must leave host mode", viewModel.uiState.value.isHost)
            assertFalse("the server must be stopped after the late start returns", isRunning.get())
            assertEquals("the transition should stop immediately and roll back the late start", 2, stopCount.get())
            assertEquals(
                "late host startup rollback must preserve shared files when joining another room",
                if (expectedSession == null) listOf(true, true) else listOf(false, false),
                stopClearSharedFiles.toList(),
            )
        } finally {
            releaseStart.countDown()
            viewModel.closeSession()
        }
    }

    @Test
    fun hostStartupReconcilesRealtimeConnectionEmittedFromWorkerDuringSnapshotReads() {
        val session = LanShareSession("http://192.168.1.2:8080")
        val connectedSnapshot = LanShareRealtimeState(browserConnected = true)
        val realtimeState = MutableStateFlow(LanShareRealtimeState())
        var emitDuringFilesSnapshot = true
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "isOnLocalNetwork", "isRouterLanHost", "browserConnected" -> false
                "localFile" -> null
                "start" -> session
                "stop", "cancelUpload", "downloadToFile", "downloadPreview" -> Unit
                "localFiles" -> {
                    if (emitDuringFilesSnapshot) {
                        emitDuringFilesSnapshot = false
                        val emitted = CountDownLatch(1)
                        Thread {
                            realtimeState.value = connectedSnapshot
                            emitted.countDown()
                        }.start()
                        check(emitted.await(5, TimeUnit.SECONDS))
                    }
                    emptyList<LanShareFile>()
                }
                "list" -> emptyList<LanShareFile>()
                "localMessages" -> emptyList<LanShareMessage>()
                "sendLocalMessage" -> error("Unexpected local message")
                "observeRealtimeState" -> realtimeState
                else -> error("Unexpected gateway operation: ${method.name}")
            }
        } as LanShareGateway
        val appContext = RuntimeEnvironment.getApplication()
        val viewModel = LanShareViewModel(
            lanShareGateway = gateway,
            previewDecoder = object : LanShareBitmapPreviewDecoder {
                override val maxDecodeEdge = 1

                override suspend fun decode(file: File, maxEdge: Int, isTiff: Boolean): Bitmap? = null
            },
            appContext = appContext,
            logger = AppLogger { _, _, _ -> },
        )

        try {
            viewModel.startHostSession()
            pumpMainUntilConnected(viewModel)

            assertTrue(
                "host UI must reconcile the latest connection after startup snapshot reads",
                viewModel.uiState.value.browserConnected,
            )
        } finally {
            viewModel.closeSession()
        }
    }

    private fun pumpMainUntilConnected(viewModel: LanShareViewModel) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val mainLooper = shadowOf(Looper.getMainLooper())
        while (!viewModel.uiState.value.browserConnected && System.nanoTime() < deadline) {
            mainLooper.idle()
            Thread.sleep(5)
        }
        mainLooper.idle()
    }

    private fun pumpMainUntilStartupSettles(viewModel: LanShareViewModel) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val mainLooper = shadowOf(Looper.getMainLooper())
        while (viewModel.uiState.value.isStartingHost && System.nanoTime() < deadline) {
            mainLooper.idle()
            Thread.sleep(5)
        }
        mainLooper.idle()
    }
}
