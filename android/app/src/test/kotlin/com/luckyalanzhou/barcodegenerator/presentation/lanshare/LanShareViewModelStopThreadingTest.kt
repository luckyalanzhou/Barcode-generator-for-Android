package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import android.app.Application
import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeState
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareViewModelStopThreadingTest {
    @Test
    fun delayedStopFromPreviousViewModelCannotCloseNewlyStartedRoom() {
        val firstStopEntered = CountDownLatch(1)
        val releaseFirstStop = CountDownLatch(1)
        val networkProbeEntered = CountDownLatch(1)
        val releaseNetworkProbe = CountDownLatch(1)
        val delayedStopEntered = CountDownLatch(1)
        val hostStartEntered = CountDownLatch(1)
        val stopCalls = AtomicInteger()
        val running = AtomicBoolean(false)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val session = LanShareSession("http://192.168.1.2:8080")
        val logger = AppLogger { _, _, _ -> }
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "stop" -> when (stopCalls.incrementAndGet()) {
                    1 -> {
                        firstStopEntered.countDown()
                        check(releaseFirstStop.await(5, TimeUnit.SECONDS))
                        running.set(false)
                        Unit
                    }
                    else -> {
                        delayedStopEntered.countDown()
                        running.set(false)
                        Unit
                    }
                }
                "isOnLocalNetwork" -> {
                    networkProbeEntered.countDown()
                    check(releaseNetworkProbe.await(5, TimeUnit.SECONDS))
                    true
                }
                "start" -> {
                    running.set(true)
                    hostStartEntered.countDown()
                    session
                }
                "isRouterLanHost", "browserConnected" -> false
                "localFile" -> null
                "localFiles" -> emptyList<LanShareFile>()
                "localMessages" -> emptyList<LanShareMessage>()
                "observeRealtimeState" -> MutableStateFlow(LanShareRealtimeState())
                else -> error("Unexpected gateway operation: ${method.name}")
            }
        } as LanShareGateway
        val stopCoordinator = LanShareGatewayStopCoordinator(gateway, applicationScope, logger)
        val previousViewModel = createViewModel(gateway, applicationScope, stopCoordinator, logger)
        var newViewModel: LanShareViewModel? = null

        try {
            previousViewModel.closeSession()
            assertTrue("first cleanup should enter", firstStopEntered.await(5, TimeUnit.SECONDS))
            val currentViewModel = createViewModel(gateway, applicationScope, stopCoordinator, logger)
            newViewModel = currentViewModel
            currentViewModel.startHostSessionAsync {}
            releaseFirstStop.countDown()
            assertTrue("new startup should pass the initial cleanup", networkProbeEntered.await(5, TimeUnit.SECONDS))

            // 旧页面销毁清理到达时，新 ViewModel 已越过等待点，协调器应拒绝旧 owner。
            previousViewModel.closeSession()
            assertTrue(
                "stale cleanup must not enqueue another stop",
                !delayedStopEntered.await(150, TimeUnit.MILLISECONDS),
            )
            releaseNetworkProbe.countDown()
            assertTrue("new host should start", hostStartEntered.await(5, TimeUnit.SECONDS))
            pumpMainUntilHostIsPublished(currentViewModel)
            assertEquals(session, currentViewModel.uiState.value.session)

            assertTrue("late cleanup from the previous ViewModel must not stop the new room", running.get())
            assertEquals(session, currentViewModel.uiState.value.session)
        } finally {
            releaseFirstStop.countDown()
            releaseNetworkProbe.countDown()
            previousViewModel.closeSession()
            newViewModel?.closeSession()
            applicationScope.cancel()
        }
    }

    @Test
    fun newViewModelWaitsForPreviousViewModelStopBeforeStartingHost() {
        val stopEntered = CountDownLatch(1)
        val releaseStop = CountDownLatch(1)
        val startEntered = CountDownLatch(1)
        val stopCalls = AtomicInteger()
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val session = LanShareSession("http://192.168.1.2:8080")
        val logger = AppLogger { _, _, _ -> }
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "stop" -> {
                    if (stopCalls.incrementAndGet() == 1) {
                        stopEntered.countDown()
                        check(releaseStop.await(5, TimeUnit.SECONDS))
                    }
                    Unit
                }
                "start" -> {
                    startEntered.countDown()
                    session
                }
                "isOnLocalNetwork" -> true
                "isRouterLanHost", "browserConnected" -> false
                "localFile" -> null
                "localFiles" -> emptyList<LanShareFile>()
                "localMessages" -> emptyList<LanShareMessage>()
                "observeRealtimeState" -> MutableStateFlow(LanShareRealtimeState())
                else -> error("Unexpected gateway operation: ${method.name}")
            }
        } as LanShareGateway
        val stopCoordinator = LanShareGatewayStopCoordinator(gateway, applicationScope, logger)
        val previousViewModel = createViewModel(gateway, applicationScope, stopCoordinator, logger)
        var newViewModel: LanShareViewModel? = null

        try {
            previousViewModel.closeSession()
            assertTrue("previous stop should enter", stopEntered.await(5, TimeUnit.SECONDS))
            val currentViewModel = createViewModel(gateway, applicationScope, stopCoordinator, logger)
            newViewModel = currentViewModel
            currentViewModel.startHostSessionAsync {}

            assertTrue(
                "new host startup must wait for the previous ViewModel cleanup",
                !startEntered.await(150, TimeUnit.MILLISECONDS),
            )
            releaseStop.countDown()
            assertTrue("new host should start after cleanup", startEntered.await(5, TimeUnit.SECONDS))
        } finally {
            releaseStop.countDown()
            previousViewModel.closeSession()
            newViewModel?.closeSession()
            applicationScope.cancel()
        }
    }

    @Test
    fun closeSchedulesGatewayStopOffCallingThread() {
        val stopEntered = CountDownLatch(1)
        val releaseStop = CountDownLatch(1)
        val stopThread = AtomicReference<Thread?>()
        val callerThread = Thread.currentThread()
        val releaseWorker = Thread {
            if (stopEntered.await(5, TimeUnit.SECONDS)) {
                Thread.sleep(100)
                releaseStop.countDown()
            }
        }.apply { start() }
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "stop" -> {
                    stopThread.set(Thread.currentThread())
                    stopEntered.countDown()
                    check(releaseStop.await(5, TimeUnit.SECONDS))
                    Unit
                }
                "observeRealtimeState" -> MutableStateFlow(LanShareRealtimeState())
                "isOnLocalNetwork", "isRouterLanHost", "browserConnected" -> false
                "localFile" -> null
                "localFiles", "localMessages", "list" -> emptyList<Any>()
                else -> error("Unexpected gateway operation: ${method.name}")
            }
        } as LanShareGateway
        val viewModel = createViewModel(gateway)

        try {
            viewModel.closeSession()
            assertTrue("gateway stop should run", stopEntered.await(5, TimeUnit.SECONDS))
            assertNotSame("server stop and file cleanup must not run on the UI caller", callerThread, stopThread.get())
        } finally {
            releaseStop.countDown()
            releaseWorker.join(TimeUnit.SECONDS.toMillis(5))
        }
    }

    private fun createViewModel(
        gateway: LanShareGateway,
        applicationScope: CoroutineScope? = null,
        stopCoordinator: LanShareGatewayStopCoordinator? = null,
        logger: AppLogger = AppLogger { _, _, _ -> },
    ): LanShareViewModel {
        val resolvedScope = applicationScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return LanShareViewModel(
            lanShareGateway = gateway,
            previewDecoder = object : LanShareBitmapPreviewDecoder {
                override val maxDecodeEdge = 1
                override suspend fun decode(file: File, maxEdge: Int, isTiff: Boolean): Bitmap? = null
            },
            appContext = RuntimeEnvironment.getApplication(),
            logger = logger,
            applicationScope = resolvedScope,
            gatewayStopCoordinator = stopCoordinator
                ?: LanShareGatewayStopCoordinator(gateway, resolvedScope, logger),
        )
    }

    private fun pumpMainUntilHostIsPublished(viewModel: LanShareViewModel) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        while (viewModel.uiState.value.session == null && System.nanoTime() < deadline) {
            mainLooper.idle()
            Thread.sleep(5)
        }
        mainLooper.idle()
        assertTrue("new host session should be published", viewModel.uiState.value.session != null)
    }
}
