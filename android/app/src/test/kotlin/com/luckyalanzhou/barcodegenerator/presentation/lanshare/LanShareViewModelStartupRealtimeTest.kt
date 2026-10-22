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
import kotlinx.coroutines.flow.MutableStateFlow
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
}
