package com.luckyalanzhou.barcodegenerator.presentation.update

import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.ApkDownloadGateway
import com.luckyalanzhou.barcodegenerator.domain.ApkValidationGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateLookupResult
import com.luckyalanzhou.barcodegenerator.presentation.UpdateCheckResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateStartupCheckTest {
    @Test
    fun repeatedActivityStartupDoesNotCancelOrDuplicateViewModelOwnedCheck() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val viewModel = updateViewModel {
            calls++
            started.complete(Unit)
            release.await()
            UpdateLookupResult.Available("1.2.3", "https://example.test/update.apk", 123L, "a".repeat(64))
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.startStartupCheck(viewModelScope)
            withTimeout(5_000) { started.await() }

            // 配置重建后的 Activity 再次调用时，应复用 ViewModel 持有的检查。
            viewModel.startStartupCheck(viewModelScope)
            assertEquals(1, calls)

            release.complete(Unit)
            assertTrue(viewModel.uiState.value.dialogShowing)
            assertEquals("1.2.3", viewModel.uiState.value.availableVersion)
            assertFalse(viewModel.uiState.value.checking)
        } finally {
            viewModelScope.cancel()
        }
    }

    @Test
    fun startupCheckWaitsForAnExistingCheckThenRetries() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val retryStarted = CompletableDeferred<Unit>()
        var calls = 0
        val viewModel = updateViewModel {
            calls++
            if (calls == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                UpdateLookupResult.UpToDate
            } else {
                retryStarted.complete(Unit)
                UpdateLookupResult.Available("1.2.3", "https://example.test/update.apk", 123L, "b".repeat(64))
            }
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val existingCheck = viewModelScope.async { viewModel.checkForUpdates() }
            withTimeout(5_000) { firstStarted.await() }

            viewModel.startStartupCheck(viewModelScope)
            assertEquals(1, calls)

            releaseFirst.complete(Unit)
            assertEquals(UpdateCheckResult.UpToDate, existingCheck.await())
            withTimeout(5_000) { retryStarted.await() }
            assertEquals(2, calls)
            assertTrue(viewModel.uiState.value.dialogShowing)
            assertEquals("1.2.3", viewModel.uiState.value.availableVersion)
        } finally {
            viewModelScope.cancel()
        }
    }

    private fun updateViewModel(check: suspend () -> UpdateLookupResult) = UpdateViewModel(
        updateDownloadGateway = object : ApkDownloadGateway {
            override fun cancel() = Unit

            override suspend fun download(
                apkUrl: String,
                expectedSize: Long?,
                expectedSha256: String?,
                onProgress: (Int, Boolean, String) -> Unit,
            ): File = error("Not used in update startup tests")
        },
        updateCatalogGateway = object : UpdateCatalogGateway {
            override suspend fun check() = check()
        },
        apkValidationGateway = object : ApkValidationGateway {
            override fun validate(file: File) = Unit
        },
        logger = AppLogger { _, _, _ -> },
        savedStateHandle = SavedStateHandle(),
    )
}
