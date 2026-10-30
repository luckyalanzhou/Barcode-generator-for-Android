package com.luckyalanzhou.barcodegenerator.presentation.update

import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.domain.*
import com.luckyalanzhou.barcodegenerator.presentation.UpdateEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class UpdateCancellationTest {
    @Test
    fun completedDownloadEventIsQueuedUntilTheUiCollectsIt() = runBlocking {
        val gateway = object : ApkDownloadGateway {
            override fun cancel() = Unit
            override suspend fun download(
                apkUrl: String,
                expectedSize: Long?,
                expectedSha256: String?,
                onProgress: (Int, Boolean, String) -> Unit,
            ) = File("ready.apk")
        }
        val coordinator = UpdateCoordinator(
            gateway,
            object : UpdateCatalogGateway { override suspend fun check() = UpdateLookupResult.UpToDate },
            object : ApkValidationGateway { override fun validate(file: File) = Unit },
            AppLogger { _, _, _ -> },
            SavedStateHandle(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            coordinator.startDownload(scope, "https://example.test/update.apk", null, null)

            assertFalse(coordinator.uiState.value.downloadRunning)
            assertEquals(
                UpdateEvent.DownloadReady(File("ready.apk").absolutePath),
                withTimeout(1000) { coordinator.events.first() },
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun ordinaryLoggerFailureDoesNotBlockDownloadStartOrReadyEvent() = runBlocking {
        val coordinator = UpdateCoordinator(
            updateDownloadGateway = object : ApkDownloadGateway {
                override fun cancel() = Unit
                override suspend fun download(
                    apkUrl: String,
                    expectedSize: Long?,
                    expectedSha256: String?,
                    onProgress: (Int, Boolean, String) -> Unit,
                ) = File("ready-after-log-failure.apk")
            },
            updateCatalogGateway = object : UpdateCatalogGateway {
                override suspend fun check() = UpdateLookupResult.UpToDate
            },
            apkValidationGateway = object : ApkValidationGateway {
                override fun validate(file: File) = Unit
            },
            logger = AppLogger { _, _, _ -> throw IOException("diagnostic sink unavailable") },
            savedStateHandle = SavedStateHandle(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            coordinator.startDownload(scope, "https://example.test/update.apk", null, null)

            assertEquals(
                UpdateEvent.DownloadReady(File("ready-after-log-failure.apk").absolutePath),
                withTimeout(1000) { coordinator.events.first() },
            )
            assertFalse(coordinator.uiState.value.downloadRunning)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun loggerFailureDoesNotSuppressDownloadFailedEvent() = runBlocking {
        val uncaught = mutableListOf<Throwable>()
        val coordinator = UpdateCoordinator(
            updateDownloadGateway = object : ApkDownloadGateway {
                override fun cancel() = Unit
                override suspend fun download(
                    apkUrl: String,
                    expectedSize: Long?,
                    expectedSha256: String?,
                    onProgress: (Int, Boolean, String) -> Unit,
                ): File = throw IOException("network unavailable")
            },
            updateCatalogGateway = object : UpdateCatalogGateway {
                override suspend fun check() = UpdateLookupResult.UpToDate
            },
            apkValidationGateway = object : ApkValidationGateway {
                override fun validate(file: File) = Unit
            },
            logger = AppLogger { _, message, _ ->
                if (message == "download failed") throw IOException("diagnostic sink unavailable")
            },
            savedStateHandle = SavedStateHandle(),
        )
        val exceptionHandler = CoroutineExceptionHandler { _, error -> uncaught += error }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + exceptionHandler)
        try {
            coordinator.startDownload(scope, "https://example.test/update.apk", null, null)

            val event = withTimeoutOrNull(1000) { coordinator.events.first() }
            assertTrue(event is UpdateEvent.DownloadFailed)
            assertTrue(uncaught.isEmpty())
            assertFalse(coordinator.uiState.value.downloadRunning)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun loggerFailureWhileRecordingCancellationDoesNotCreateDownloadFailure() = runBlocking {
        val downloadStarted = CompletableDeferred<Unit>()
        val releaseDownload = CompletableDeferred<File>()
        val cancellationRecorded = CompletableDeferred<Unit>()
        val uncaught = mutableListOf<Throwable>()
        var cancellations = 0
        val coordinator = UpdateCoordinator(
            updateDownloadGateway = object : ApkDownloadGateway {
                override fun cancel() { cancellations++ }
                override suspend fun download(
                    apkUrl: String,
                    expectedSize: Long?,
                    expectedSha256: String?,
                    onProgress: (Int, Boolean, String) -> Unit,
                ): File {
                    downloadStarted.complete(Unit)
                    return releaseDownload.await()
                }
            },
            updateCatalogGateway = object : UpdateCatalogGateway {
                override suspend fun check() = UpdateLookupResult.UpToDate
            },
            apkValidationGateway = object : ApkValidationGateway {
                override fun validate(file: File) = Unit
            },
            logger = AppLogger { _, message, _ ->
                if (message == "download cancelled operation=1") {
                    cancellationRecorded.complete(Unit)
                    throw IOException("diagnostic sink unavailable")
                }
            },
            savedStateHandle = SavedStateHandle(),
        )
        val exceptionHandler = CoroutineExceptionHandler { _, error -> uncaught += error }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + exceptionHandler)
        try {
            coordinator.startDownload(scope, "https://example.test/update.apk", null, null)
            withTimeout(1000) { downloadStarted.await() }
            coordinator.cancelDownload()
            withTimeout(1000) { cancellationRecorded.await() }

            assertNull(withTimeoutOrNull(50) { coordinator.events.first() })
            assertTrue(uncaught.isEmpty())
            assertFalse(coordinator.uiState.value.downloadRunning)
            assertEquals(1, cancellations)
        } finally {
            scope.cancel()
            releaseDownload.complete(File("unused.apk"))
        }
    }

    @Test
    fun cancelledRequestCannotPublishProgressOverNewDownload() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstEnded = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        var cancellations = 0
        val gateway = object : ApkDownloadGateway {
            override fun cancel() { cancellations++ }
            override suspend fun download(apkUrl: String, expectedSize: Long?, expectedSha256: String?, onProgress: (Int, Boolean, String) -> Unit): File {
                if (calls.incrementAndGet() == 1) {
                    firstStarted.complete(Unit)
                    withContext(NonCancellable) {
                        releaseFirst.await()
                        onProgress(99, false, "old request")
                        firstEnded.complete(Unit)
                    }
                    throw CancellationException()
                }
                onProgress(20, false, "new request")
                secondStarted.complete(Unit)
                releaseSecond.await()
                return File("not-created-test.apk")
            }
        }
        val coordinator = UpdateCoordinator(gateway, object : UpdateCatalogGateway {
            override suspend fun check() = UpdateLookupResult.UpToDate
        }, object : ApkValidationGateway { override fun validate(file: File) = Unit }, AppLogger { _, _, _ -> },
            SavedStateHandle())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            coordinator.startDownload(scope, "https://example.test/first", null, null)
            withTimeout(5000) { firstStarted.await() }
            coordinator.cancelDownload()
            coordinator.startDownload(scope, "https://example.test/second", null, null)
            withTimeout(5000) { secondStarted.await() }
            releaseFirst.complete(Unit)
            withTimeout(5000) { firstEnded.await() }
            assertEquals(20, coordinator.downloadUiState.value.progress)
            assertTrue(coordinator.uiState.value.downloadRunning)
            assertEquals(1, cancellations)
        } finally {
            scope.cancel()
            releaseSecond.complete(Unit)
            releaseFirst.complete(Unit)
        }
    }
}
