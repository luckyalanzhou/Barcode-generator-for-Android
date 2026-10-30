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
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareViewModelLoggerFailureTest {
    @Test
    fun uploadContinuesWhenSourceDiagnosticThrows() {
        val session = LanShareSession("http://192.168.1.2:8080")
        val uploadStarted = CountDownLatch(1)
        val terminalEvent = CountDownLatch(1)
        val events = CopyOnWriteArrayList<LanShareEvent>()
        val realtimeState = MutableStateFlow(LanShareRealtimeState())
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
                    uploadStarted.countDown()
                    "uploaded-file"
                }
                else -> error("Unexpected gateway operation: ${method.name}")
            }
        } as LanShareGateway
        val appContext = RuntimeEnvironment.getApplication()
        val uploadSourceFile = File.createTempFile("lan-log-failure-", ".txt", appContext.cacheDir)
        uploadSourceFile.writeText("safe test payload")
        val viewModel = LanShareViewModel(
            lanShareGateway = gateway,
            previewDecoder = object : LanShareBitmapPreviewDecoder {
                override val maxDecodeEdge = 1

                override suspend fun decode(file: File, maxEdge: Int, isTiff: Boolean): Bitmap? = null
            },
            appContext = appContext,
            logger = AppLogger { _, message, _ ->
                if (message.startsWith("upload source")) error("diagnostic storage unavailable")
            },
        )
        val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val eventCollector = eventScope.launch {
            viewModel.events.collect { event ->
                events += event
                if (event is LanShareEvent.Notice || event is LanShareEvent.Error) terminalEvent.countDown()
            }
        }

        try {
            viewModel.startHostSession()
            viewModel.uploadFile(session, Uri.fromFile(uploadSourceFile))

            assertTrue("the upload should finish even if its diagnostic fails", terminalEvent.await(5, TimeUnit.SECONDS))
            assertTrue("the upload Gateway must receive the file", uploadStarted.count == 0L)
            assertTrue("a successful upload must report success", events.any { it == LanShareEvent.Notice("上传成功") })
            assertTrue("a diagnostic failure must not be reported as an upload failure", events.none { it is LanShareEvent.Error })
        } finally {
            eventCollector.cancel()
            eventScope.cancel()
            viewModel.closeSession()
            uploadSourceFile.delete()
        }
    }
}
