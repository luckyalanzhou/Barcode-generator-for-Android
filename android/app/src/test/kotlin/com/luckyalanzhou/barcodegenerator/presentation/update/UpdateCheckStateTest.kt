package com.luckyalanzhou.barcodegenerator.presentation.update

import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.domain.*
import com.luckyalanzhou.barcodegenerator.presentation.UpdateCheckResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class UpdateCheckStateTest {
    private fun coordinator(
        logger: AppLogger = AppLogger { _, _, _ -> },
        lookup: suspend () -> UpdateLookupResult,
    ) = UpdateCoordinator(
        object : ApkDownloadGateway {
            override fun cancel() = Unit
            override suspend fun download(apkUrl: String, expectedSize: Long?, expectedSha256: String?,
                onProgress: (Int, Boolean, String) -> Unit): File = error("not used")
        }, object : UpdateCatalogGateway { override suspend fun check() = lookup() },
        object : ApkValidationGateway { override fun validate(file: File) = Unit }, logger,
        SavedStateHandle())

    @Test fun duplicateClickDoesNotStartAnotherCheck() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = coordinator { calls++; started.complete(Unit); release.await(); UpdateLookupResult.UpToDate }
        val first = async { coordinator.checkForUpdates() }
        withTimeout(5000) { started.await() }
        assertTrue(coordinator.uiState.value.checking)
        assertEquals(UpdateCheckResult.InProgress, coordinator.checkForUpdates())
        assertEquals(1, calls)
        release.complete(Unit)
        assertEquals(UpdateCheckResult.UpToDate, first.await())
        assertFalse(coordinator.uiState.value.checking)
    }

    @Test fun cancellationReleasesBusyStateAndAllowsRetry() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var first = true
        val coordinator = coordinator {
            if (first) { first = false; started.complete(Unit); awaitCancellation() }
            UpdateLookupResult.UpToDate
        }
        val job = launch { coordinator.checkForUpdates() }
        withTimeout(5000) { started.await() }
        job.cancelAndJoin()
        assertFalse(coordinator.uiState.value.checking)
        assertEquals(UpdateCheckResult.UpToDate, coordinator.checkForUpdates())
    }

    @Test fun interruptedCheckRestoresThreadFlagAndAllowsRetry() = runBlocking {
        val interruption = InterruptedException("update request interrupted")
        var firstCheck = true
        val coordinator = coordinator {
            if (firstCheck) {
                firstCheck = false
                throw interruption
            }
            UpdateLookupResult.UpToDate
        }

        try {
            assertSame(interruption, runCatching { coordinator.checkForUpdates() }.exceptionOrNull())
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
        assertFalse(coordinator.uiState.value.checking)
        assertEquals(UpdateCheckResult.UpToDate, coordinator.checkForUpdates())
    }

    @Test fun loggerFailureWhileRecordingCancellationDoesNotReplaceCancellation() = runBlocking {
        val cancellation = CancellationException("update check cancelled")
        val loggerFailure = IOException("diagnostic sink unavailable")
        var firstCheck = true
        var catalogChecks = 0
        val coordinator = UpdateCoordinator(
            updateDownloadGateway = object : ApkDownloadGateway {
                override fun cancel() = Unit
                override suspend fun download(
                    apkUrl: String,
                    expectedSize: Long?,
                    expectedSha256: String?,
                    onProgress: (Int, Boolean, String) -> Unit,
                ): File = error("not used")
            },
            updateCatalogGateway = object : UpdateCatalogGateway {
                override suspend fun check(): UpdateLookupResult {
                    catalogChecks++
                    if (firstCheck) {
                        firstCheck = false
                        throw cancellation
                    }
                    return UpdateLookupResult.UpToDate
                }
            },
            apkValidationGateway = object : ApkValidationGateway {
                override fun validate(file: File) = Unit
            },
            logger = AppLogger { _, message, _ ->
                if (message == "check cancelled") throw loggerFailure
            },
            savedStateHandle = SavedStateHandle(),
        )

        assertSame(cancellation, runCatching { coordinator.checkForUpdates() }.exceptionOrNull())
        assertArrayEquals(arrayOf(loggerFailure), cancellation.suppressed)
        assertFalse(coordinator.uiState.value.checking)
        assertEquals(UpdateCheckResult.UpToDate, coordinator.checkForUpdates())
        assertEquals(2, catalogChecks)
    }

    @Test fun unexpectedFailureReleasesBusyState() = runBlocking {
        val coordinator = coordinator { error("network failure") }
        assertTrue(coordinator.checkForUpdates() is UpdateCheckResult.Failed)
        assertFalse(coordinator.uiState.value.checking)
        assertTrue(coordinator.checkForUpdates() is UpdateCheckResult.Failed)
    }

    @Test fun loggerFailureDoesNotChangeSuccessfulCheckResult() = runBlocking {
        val coordinator = coordinator(
            logger = AppLogger { _, _, _ -> throw IOException("diagnostic sink unavailable") },
        ) { UpdateLookupResult.UpToDate }

        assertEquals(UpdateCheckResult.UpToDate, coordinator.checkForUpdates())
    }

    @Test fun loggerFailureWhileAnotherCheckRunsStillReturnsInProgress() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = coordinator(
            logger = AppLogger { _, message, _ ->
                if (message == "check skipped reason=in_progress") {
                    throw IOException("diagnostic sink unavailable")
                }
            },
        ) {
            started.complete(Unit)
            release.await()
            UpdateLookupResult.UpToDate
        }
        val firstCheck = async { coordinator.checkForUpdates() }
        withTimeout(5000) { started.await() }

        assertEquals(UpdateCheckResult.InProgress, coordinator.checkForUpdates())
        release.complete(Unit)
        assertEquals(UpdateCheckResult.UpToDate, firstCheck.await())
    }

    @Test fun loggerFailureWhileReportingCatalogFailureKeepsRecoverableResult() = runBlocking {
        val loggerFailure = IOException("diagnostic sink unavailable")
        var firstCheck = true
        var catalogChecks = 0
        val coordinator = coordinator(
            lookup = {
                catalogChecks++
                if (firstCheck) {
                    firstCheck = false
                    throw IOException("catalog unavailable")
                }
                UpdateLookupResult.UpToDate
            },
            logger = AppLogger { _, message, _ ->
                if (message == "check failed exception") throw loggerFailure
            },
        )

        assertEquals(
            UpdateCheckResult.Failed("检查更新失败，请重试"),
            coordinator.checkForUpdates(),
        )
        assertEquals(UpdateCheckResult.UpToDate, coordinator.checkForUpdates())
        assertEquals(2, catalogChecks)
        assertFalse(coordinator.uiState.value.checking)
    }

    @Test fun loggerFailureOnCheckStartDoesNotLeakCheckMutex() = runBlocking {
        val loggerFailure = AssertionError("diagnostic logger failed")
        var failFirstStartLog = true
        var catalogChecks = 0
        val coordinator = UpdateCoordinator(
            updateDownloadGateway = object : ApkDownloadGateway {
                override fun cancel() = Unit
                override suspend fun download(
                    apkUrl: String,
                    expectedSize: Long?,
                    expectedSha256: String?,
                    onProgress: (Int, Boolean, String) -> Unit,
                ): File = error("not used")
            },
            updateCatalogGateway = object : UpdateCatalogGateway {
                override suspend fun check(): UpdateLookupResult {
                    catalogChecks++
                    return UpdateLookupResult.UpToDate
                }
            },
            apkValidationGateway = object : ApkValidationGateway {
                override fun validate(file: File) = Unit
            },
            logger = AppLogger { _, message, _ ->
                if (message == "check start" && failFirstStartLog) {
                    failFirstStartLog = false
                    throw loggerFailure
                }
            },
            savedStateHandle = SavedStateHandle(),
        )

        assertSame(loggerFailure, runCatching { coordinator.checkForUpdates() }.exceptionOrNull())
        assertEquals(UpdateCheckResult.UpToDate, coordinator.checkForUpdates())
        assertEquals(1, catalogChecks)
        assertFalse(coordinator.uiState.value.checking)
    }

    @Test fun pendingInstallPathSurvivesCoordinatorRecreationAndIsConsumedOnce() {
        val savedState = SavedStateHandle()
        val original = coordinatorWithState(savedState)
        val expectedPath = File("update.apk").absolutePath

        original.setPendingInstallPath(expectedPath)
        val recreatedState = SavedStateHandle(savedState.keys().associateWith { key ->
            savedState.get<Any>(key)
        })
        val recreated = coordinatorWithState(recreatedState)

        assertEquals(expectedPath, recreated.uiState.value.pendingInstallPath)
        assertEquals(expectedPath, recreated.takePendingInstallPath())
        assertNull(recreated.uiState.value.pendingInstallPath)
        assertNull(recreated.takePendingInstallPath())
    }

    private fun coordinatorWithState(savedState: SavedStateHandle) = UpdateCoordinator(
        object : ApkDownloadGateway {
            override fun cancel() = Unit
            override suspend fun download(apkUrl: String, expectedSize: Long?, expectedSha256: String?,
                onProgress: (Int, Boolean, String) -> Unit): File = error("not used")
        },
        object : UpdateCatalogGateway { override suspend fun check() = UpdateLookupResult.UpToDate },
        object : ApkValidationGateway { override fun validate(file: File) = Unit },
        AppLogger { _, _, _ -> },
        savedState,
    )
}
