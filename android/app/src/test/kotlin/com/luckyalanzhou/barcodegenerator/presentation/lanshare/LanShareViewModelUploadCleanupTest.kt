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
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
