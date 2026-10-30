package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareDownloadCancellation
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeState
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareViewModelDownloadCancellationTest {
    @Test
    fun cancellationDuringBlockedDownloadIsNotLoggedAsTransferFailure() {
        val downloadEntered = CountDownLatch(1)
        val allowDownloadToFail = CountDownLatch(1)
        val downloadLogged = CountDownLatch(1)
        val cancellationSignal = AtomicReference<LanShareDownloadCancellation?>()
        val logMessages = CopyOnWriteArrayList<String>()
        val session = LanShareSession("http://192.168.1.2:8080")
        val realtimeState = MutableStateFlow(LanShareRealtimeState())
        val logger = AppLogger { _, message, _ ->
            logMessages += message
            if (message.startsWith("download cancelled") || message.startsWith("download failed")) {
                downloadLogged.countDown()
            }
        }
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, args ->
            when (method.name) {
                "isOnLocalNetwork", "isRouterLanHost", "browserConnected" -> false
                "localFile" -> null
                "start" -> session
                "stop", "cancelUpload" -> Unit
                "localFiles", "list" -> emptyList<LanShareFile>()
                "localMessages" -> emptyList<LanShareMessage>()
                "sendLocalMessage" -> error("Unexpected local message")
                "observeRealtimeState" -> realtimeState
                "downloadToFile" -> {
                    cancellationSignal.set(args?.get(3) as LanShareDownloadCancellation)
                    downloadEntered.countDown()
                    assertTrue("fake download was not released", allowDownloadToFail.await(5, TimeUnit.SECONDS))
                    throw IOException("connection dropped during cancellation")
                }
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
            logger = logger,
        )
        val events = CopyOnWriteArrayList<LanShareEvent>()
        val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val eventCollector = eventScope.launch { viewModel.events.collect { events.add(it) } }

        try {
            viewModel.startHostSession()
            viewModel.downloadFile(session, "remote-id", Uri.parse("content://missing.provider/target"))
            assertTrue("download did not enter the gateway", downloadEntered.await(5, TimeUnit.SECONDS))

            viewModel.closeSession()
            assertTrue("closing the room must cancel its active download signal", cancellationSignal.get()?.isCancelled == true)
            allowDownloadToFail.countDown()

            assertTrue("cancelled download did not finish its ViewModel path", downloadLogged.await(5, TimeUnit.SECONDS))
            assertTrue(logMessages.any { it.startsWith("download cancelled") })
            assertFalse(logMessages.any { it.startsWith("download failed") })
            assertTrue("a cancelled download must not emit an error event", events.isEmpty())
        } finally {
            allowDownloadToFail.countDown()
            eventCollector.cancel()
            eventScope.cancel()
            viewModel.closeSession()
        }
    }

    @Test
    fun closingSessionCancelsBlockedDownloadBeforeOpeningSafDestination() {
        val downloadEntered = CountDownLatch(1)
        val allowDownloadToReturn = CountDownLatch(1)
        val cancellationHandled = CountDownLatch(1)
        val cancellationSignal = AtomicReference<LanShareDownloadCancellation?>()
        val session = LanShareSession("http://192.168.1.2:8080")
        val realtimeState = MutableStateFlow(LanShareRealtimeState())
        val logger = AppLogger { _, message, _ ->
            if (message.startsWith("download cancelled")) cancellationHandled.countDown()
        }
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, args ->
            when (method.name) {
                "isOnLocalNetwork", "isRouterLanHost", "browserConnected" -> false
                "localFile" -> null
                "start" -> session
                "stop", "cancelUpload" -> Unit
                "localFiles", "list" -> emptyList<LanShareFile>()
                "localMessages" -> emptyList<LanShareMessage>()
                "sendLocalMessage" -> error("Unexpected local message")
                "observeRealtimeState" -> realtimeState
                "downloadToFile" -> {
                    cancellationSignal.set(args?.get(3) as LanShareDownloadCancellation)
                    downloadEntered.countDown()
                    assertTrue("fake download was not released", allowDownloadToReturn.await(5, TimeUnit.SECONDS))
                    Unit
                }
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
            logger = logger,
        )
        val events = CopyOnWriteArrayList<LanShareEvent>()
        val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val eventCollector = eventScope.launch { viewModel.events.collect { events.add(it) } }

        try {
            viewModel.startHostSession()
            viewModel.downloadFile(session, "remote-id", Uri.parse("content://missing.provider/target"))
            assertTrue("download did not enter the gateway", downloadEntered.await(5, TimeUnit.SECONDS))

            viewModel.closeSession()
            assertTrue("closing the room must cancel its active download signal", cancellationSignal.get()?.isCancelled == true)
            allowDownloadToReturn.countDown()

            assertTrue("cancelled download did not finish its ViewModel path", cancellationHandled.await(5, TimeUnit.SECONDS))
            assertTrue("closing a session must not attempt to write the SAF destination", events.isEmpty())
        } finally {
            allowDownloadToReturn.countDown()
            eventCollector.cancel()
            eventScope.cancel()
            viewModel.closeSession()
        }
    }
}
