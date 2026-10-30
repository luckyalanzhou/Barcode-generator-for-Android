package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeState
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareViewModelUploadCleanupTest {
    @Test
    fun cancellationDuringBlockedUploadIsNotLoggedAsTransferFailure() {
        val uploadEntered = CountDownLatch(1)
        val allowUploadToFail = CountDownLatch(1)
        val uploadLogged = CountDownLatch(1)
        val logMessages = CopyOnWriteArrayList<String>()
        val session = LanShareSession("http://192.168.1.2:8080")
        val realtimeState = MutableStateFlow(LanShareRealtimeState())
        val logger = AppLogger { _, message, _ ->
            logMessages += message
            if (message.startsWith("upload cancelled") || message.startsWith("upload failed")) {
                uploadLogged.countDown()
            }
        }
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "isOnLocalNetwork", "isRouterLanHost", "browserConnected" -> false
                "localFile" -> null
                "start" -> session
                "stop", "cancelUpload", "downloadToFile", "downloadPreview" -> Unit
                "localFiles", "list" -> emptyList<LanShareFile>()
                "localMessages" -> emptyList<LanShareMessage>()
                "sendLocalMessage" -> error("Unexpected local message")
                "observeRealtimeState" -> realtimeState
                "upload" -> {
                    uploadEntered.countDown()
                    assertTrue("fake upload was not released", allowUploadToFail.await(5, TimeUnit.SECONDS))
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
        val uploadFile = File.createTempFile("lan-upload-cancel-", ".bin", appContext.cacheDir)
        uploadFile.writeBytes(byteArrayOf(1, 2, 3))
        val events = CopyOnWriteArrayList<LanShareEvent>()
        val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val eventCollector = eventScope.launch { viewModel.events.collect { events.add(it) } }

        try {
            viewModel.startHostSession()
            viewModel.uploadFile(session, Uri.fromFile(uploadFile), uploadFile)
            assertTrue("upload did not enter the blocking gateway", uploadEntered.await(5, TimeUnit.SECONDS))

            viewModel.closeSession()
            allowUploadToFail.countDown()

            assertTrue("cancelled upload did not finish its ViewModel path", uploadLogged.await(5, TimeUnit.SECONDS))
            assertTrue(logMessages.any { it.startsWith("upload cancelled") })
            assertFalse(logMessages.any { it.startsWith("upload failed") })
            assertTrue("a cancelled upload must not emit an error event", events.isEmpty())
        } finally {
            allowUploadToFail.countDown()
            eventCollector.cancel()
            eventScope.cancel()
            viewModel.closeSession()
            uploadFile.delete()
        }
    }

    @Test
    fun closingSessionDeletesCaptureFileWhileSynchronousUploadIsStillBlocked() {
        val uploadEntered = CountDownLatch(1)
        val releaseUpload = CountDownLatch(1)
        val session = LanShareSession("http://192.168.1.2:8080")
        val realtimeState = MutableStateFlow(LanShareRealtimeState())
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, args ->
            when (method.name) {
                "isOnLocalNetwork", "isRouterLanHost", "browserConnected" -> false
                "localFile" -> null
                "start" -> session
                "stop", "cancelUpload", "downloadToFile", "downloadPreview" -> Unit
                "localFiles", "list" -> emptyList<LanShareFile>()
                "localMessages" -> emptyList<LanShareMessage>()
                "sendLocalMessage" -> error("Unexpected local message")
                "observeRealtimeState" -> realtimeState
                "upload" -> {
                    uploadEntered.countDown()
                    assertTrue("fake upload was not released", releaseUpload.await(5, TimeUnit.SECONDS))
                    "uploaded-file"
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
            logger = AppLogger { _, _, _ -> },
        )
        val capturedPhoto = File.createTempFile("lan-upload-cleanup-", ".jpg", appContext.cacheDir)
        capturedPhoto.writeBytes(byteArrayOf(1, 2, 3))

        try {
            viewModel.startHostSession()
            viewModel.uploadFile(session, Uri.fromFile(capturedPhoto), capturedPhoto)
            assertTrue("upload did not enter the blocking gateway", uploadEntered.await(5, TimeUnit.SECONDS))

            viewModel.closeSession()

            assertFalse(
                "closing the room must delete its capture temp file without waiting for the upload call to return",
                capturedPhoto.exists(),
            )
        } finally {
            releaseUpload.countDown()
            viewModel.closeSession()
            capturedPhoto.delete()
        }
    }
}
