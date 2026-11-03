package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import android.app.Application
import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeState
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareViewModelRefreshTest {
    @Test
    fun refreshRequestedDuringActiveSnapshotIsNotDropped() {
        val session = LanShareSession("http://192.168.1.2:8080")
        val oldFile = LanShareFile("old", "old.txt", 1L, 1L)
        val newFile = LanShareFile("new", "new.txt", 1L, 2L)
        val remoteFiles = AtomicReference(listOf(oldFile))
        val listCalls = AtomicInteger()
        val firstSnapshotCaptured = CountDownLatch(1)
        val releaseFirstSnapshot = CountDownLatch(1)
        val gateway = Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "stop", "cancelUpload", "downloadToFile", "downloadPreview" -> Unit
                "localFile" -> null
                "browserConnected", "isOnLocalNetwork", "isRouterLanHost" -> false
                "localFiles", "localMessages" -> emptyList<Any>()
                "observeRealtimeState" -> MutableStateFlow(LanShareRealtimeState())
                "list" -> {
                    val snapshot = remoteFiles.get()
                    if (listCalls.incrementAndGet() == 1) {
                        firstSnapshotCaptured.countDown()
                        check(releaseFirstSnapshot.await(5, TimeUnit.SECONDS))
                    }
                    snapshot
                }
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
            viewModel.joinSession(session)
            viewModel.refreshFiles(session)
            assertTrue("the initial snapshot request did not start", firstSnapshotCaptured.await(5, TimeUnit.SECONDS))

            remoteFiles.set(listOf(newFile))
            viewModel.refreshFiles(session)
        } finally {
            releaseFirstSnapshot.countDown()
        }

        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (
                (listCalls.get() < 2 || viewModel.uiState.value.files.map(LanShareFile::id) != listOf(newFile.id)) &&
                System.nanoTime() < deadline
            ) Thread.sleep(5)

            assertEquals("a refresh requested during an active snapshot must run after it", 2, listCalls.get())
            assertEquals(listOf(newFile.id), viewModel.uiState.value.files.map(LanShareFile::id))
        } finally {
            viewModel.closeSession()
        }
    }
}
