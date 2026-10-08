package com.luckyalanzhou.barcodegenerator.presentation.update

import com.luckyalanzhou.barcodegenerator.domain.*
import com.luckyalanzhou.barcodegenerator.presentation.UpdateCheckResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateCheckStateTest {
    private fun coordinator(lookup: suspend () -> UpdateLookupResult) = UpdateCoordinator(
        object : ApkDownloadGateway {
            override fun cancel() = Unit
            override suspend fun download(apkUrl: String, expectedSize: Long?, expectedSha256: String?,
                onProgress: (Int, Boolean, String) -> Unit): File = error("not used")
        }, object : UpdateCatalogGateway { override suspend fun check() = lookup() },
        object : ApkValidationGateway { override fun validate(file: File) = Unit }, AppLogger { _, _, _ -> })

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

    @Test fun unexpectedFailureReleasesBusyState() = runBlocking {
        val coordinator = coordinator { error("network failure") }
        assertTrue(coordinator.checkForUpdates() is UpdateCheckResult.Failed)
        assertFalse(coordinator.uiState.value.checking)
        assertTrue(coordinator.checkForUpdates() is UpdateCheckResult.Failed)
    }
}
