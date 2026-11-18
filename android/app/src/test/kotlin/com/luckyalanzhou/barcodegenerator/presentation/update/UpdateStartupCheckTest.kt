package com.luckyalanzhou.barcodegenerator.presentation.update

import com.luckyalanzhou.barcodegenerator.R
import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.ApkDownloadGateway
import com.luckyalanzhou.barcodegenerator.domain.ApkValidationGateway
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.domain.UpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateLookupResult
import com.luckyalanzhou.barcodegenerator.presentation.UpdateCheckResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateStartupCheckTest {
    @Test
    fun startupCheckDoesNotShowTheIgnoredVersion() = runBlocking {
        val settings = FakeSettingsRepository("1.2.3")
        val viewModel = updateViewModel(
            check = { UpdateLookupResult.Available("1.2.3", "https://example.test/update.apk", 123L, "a".repeat(64)) },
            settingsRepository = settings,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.startStartupCheck(scope)

            assertEquals("1.2.3", viewModel.uiState.value.availableVersion)
            assertFalse(viewModel.uiState.value.dialogShowing)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startupCheckShowsAReleaseThatIsNotIgnored() = runBlocking {
        val viewModel = updateViewModel(
            check = { UpdateLookupResult.Available("1.2.4", "https://example.test/update.apk", 123L, "a".repeat(64)) },
            settingsRepository = FakeSettingsRepository("1.2.3"),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.startStartupCheck(scope)

            assertTrue(viewModel.uiState.value.dialogShowing)
            assertEquals("1.2.4", viewModel.uiState.value.availableVersion)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startupCheckShowsNewBetaBuildWithSameVersionName() = runBlocking {
        val viewModel = updateViewModel(
            check = {
                UpdateLookupResult.Available(
                    "9.9.9",
                    "https://example.test/update.apk",
                    123L,
                    "a".repeat(64),
                    ignoreKey = "9.9.9#19",
                )
            },
            settingsRepository = FakeSettingsRepository("9.9.9#18"),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.startStartupCheck(scope)

            assertTrue(viewModel.uiState.value.dialogShowing)
            assertEquals("9.9.9", viewModel.uiState.value.availableVersion)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startupCheckMigratesLegacyIgnoredVersionNameToCurrentBuildIdentity() = runBlocking {
        val settings = FakeSettingsRepository("9.9.9")
        val viewModel = updateViewModel(
            check = {
                UpdateLookupResult.Available(
                    "9.9.9",
                    "https://example.test/update.apk",
                    123L,
                    "a".repeat(64),
                    ignoreKey = "9.9.9#18",
                )
            },
            settingsRepository = settings,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.startStartupCheck(scope)

            assertFalse(viewModel.uiState.value.dialogShowing)
            assertEquals("9.9.9#18", settings.ignoredVersion)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun manualCheckStillReturnsAnIgnoredVersionForExplicitDisplay() = runBlocking {
        val viewModel = updateViewModel(
            check = { UpdateLookupResult.Available("1.2.3", "https://example.test/update.apk", 123L, "a".repeat(64)) },
            settingsRepository = FakeSettingsRepository("1.2.3"),
        )

        assertTrue(viewModel.checkForUpdates() is UpdateCheckResult.Available)
    }

    @Test
    fun manualCheckDuringStartupWaitsAndReturnsIgnoredVersionForExplicitDisplay() = runBlocking {
        val firstCheckStarted = CompletableDeferred<Unit>()
        val releaseFirstCheck = CompletableDeferred<Unit>()
        var checks = 0
        val viewModel = updateViewModel(
            settingsRepository = FakeSettingsRepository("1.2.3"),
        ) {
            checks++
            if (checks == 1) {
                firstCheckStarted.complete(Unit)
                releaseFirstCheck.await()
            }
            UpdateLookupResult.Available("1.2.3", "https://example.test/update.apk", 123L, "a".repeat(64))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.startStartupCheck(scope)
            firstCheckStarted.await()

            val manualCheck = scope.async { viewModel.checkForUpdates() }
            releaseFirstCheck.complete(Unit)

            assertTrue(manualCheck.await() is UpdateCheckResult.Available)
            assertEquals(2, checks)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun manualCheckCompletesAfterTheRequestingActivityScopeIsCancelled() = runBlocking {
        val checkStarted = CompletableDeferred<Unit>()
        val releaseCheck = CompletableDeferred<Unit>()
        val viewModel = updateViewModel(
            settingsRepository = FakeSettingsRepository("1.2.3"),
        ) {
            checkStarted.complete(Unit)
            releaseCheck.await()
            UpdateLookupResult.Available("1.2.3", "https://example.test/update.apk", 123L, "a".repeat(64))
        }
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requestingActivityScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            requestingActivityScope.launch { viewModel.startManualCheck(viewModelScope) }
            checkStarted.await()
            requestingActivityScope.cancel()
            releaseCheck.complete(Unit)

            assertTrue(viewModel.uiState.value.dialogShowing)
            assertTrue(viewModel.manualCheckResults.first() is UpdateCheckResult.Available)
        } finally {
            requestingActivityScope.cancel()
            viewModelScope.cancel()
        }
    }

    @Test
    fun startupCheckDoesNotShowStaleUpdateAfterManualCheckClearsIt() = runBlocking {
        val settings = FakeSettingsRepository().apply {
            readStarted = CompletableDeferred()
            releaseRead = CompletableDeferred()
        }
        var checks = 0
        val viewModel = updateViewModel(settingsRepository = settings) {
            if (checks++ == 0) {
                UpdateLookupResult.Available("1.2.3", "https://example.test/old.apk", 123L, "a".repeat(64))
            } else {
                UpdateLookupResult.UpToDate
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.startStartupCheck(scope)
            settings.readStarted?.await()

            assertEquals(UpdateCheckResult.UpToDate, viewModel.checkForUpdates())
            assertEquals(null, viewModel.uiState.value.availableVersion)
            settings.releaseRead?.complete(Unit)

            assertFalse(viewModel.uiState.value.dialogShowing)
            assertEquals(null, viewModel.uiState.value.availableVersion)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun ignoreAvailableUpdatePersistsTheVersionBeforeDismissing() = runBlocking {
        val settings = FakeSettingsRepository()
        val viewModel = updateViewModel(check = { UpdateLookupResult.UpToDate }, settingsRepository = settings)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.setAvailableUpdate("1.2.3", "https://example.test/update.apk", 123L, "a".repeat(64), "1.2.3#18")
            viewModel.setDialogShowing(true)

            viewModel.ignoreAvailableUpdate(scope, onFailure = {})

            assertEquals("1.2.3#18", settings.ignoredVersion)
            assertFalse(viewModel.uiState.value.dialogShowing)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun finishingIgnoreForPreviousVersionDoesNotDismissNewVersionDialog() = runBlocking {
        val settings = FakeSettingsRepository().apply {
            writeStarted = CompletableDeferred()
            releaseWrite = CompletableDeferred()
        }
        val viewModel = updateViewModel(settingsRepository = settings) { UpdateLookupResult.UpToDate }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.setAvailableUpdate("1.2.3", "https://example.test/old.apk", 123L, "a".repeat(64))
            viewModel.setDialogShowing(true)

            viewModel.ignoreAvailableUpdate(scope, onFailure = {})
            settings.writeStarted?.await()

            // 检查到新版本后打开的新弹窗，不能被旧版本的异步忽略操作关闭。
            viewModel.setAvailableUpdate("1.2.4", "https://example.test/new.apk", 456L, "b".repeat(64))
            viewModel.setDialogShowing(true)
            settings.releaseWrite?.complete(Unit)

            assertEquals("1.2.4", viewModel.uiState.value.availableVersion)
            assertTrue(viewModel.uiState.value.dialogShowing)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedIgnorePersistenceKeepsTheUpdateDialogOpen() = runBlocking {
        val settings = FakeSettingsRepository().apply { writeFailure = IOException("settings unavailable") }
        val logs = mutableListOf<String>()
        val viewModel = updateViewModel(
            settingsRepository = settings,
            logger = AppLogger { _, message, _ -> logs += message },
        ) { UpdateLookupResult.UpToDate }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            viewModel.setAvailableUpdate("1.2.3", "https://example.test/update.apk", 123L, "a".repeat(64))
            viewModel.setDialogShowing(true)

            var surfacedError: Int? = null
            viewModel.ignoreAvailableUpdate(scope, onFailure = { surfacedError = it })

            assertTrue(viewModel.uiState.value.dialogShowing)
            assertTrue(logs.any { it.contains("ignore failed") })
            assertEquals(R.string.ignore_update_failed, surfacedError)
        } finally {
            scope.cancel()
        }
    }

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

    private fun updateViewModel(
        settingsRepository: SettingsRepository = FakeSettingsRepository(),
        logger: AppLogger = AppLogger { _, _, _ -> },
        check: suspend () -> UpdateLookupResult,
    ) = UpdateViewModel(
        updateDownloadGateway = object : ApkDownloadGateway {
            override fun cancel() = Unit

            override suspend fun download(
                apkUrl: String,
                expectedSize: Long?,
                expectedSha256: String?,
                onProgress: (Int, Boolean, Long) -> Unit,
            ): File = error("Not used in update startup tests")
        },
        updateCatalogGateway = object : UpdateCatalogGateway {
            override suspend fun check() = check()
        },
        apkValidationGateway = object : ApkValidationGateway {
            override fun validate(file: File) = Unit
        },
        logger = logger,
        savedStateHandle = SavedStateHandle(),
        settingsRepository = settingsRepository,
    )

    private class FakeSettingsRepository(var ignoredVersion: String? = null) : SettingsRepository {
        var writeFailure: Exception? = null
        var writeStarted: CompletableDeferred<Unit>? = null
        var releaseWrite: CompletableDeferred<Unit>? = null
        var readStarted: CompletableDeferred<Unit>? = null
        var releaseRead: CompletableDeferred<Unit>? = null
        override suspend fun load() = Unit
        override fun loadStyle() = StyleSettings()
        override fun getLiquidGlassStrengthPercent() = 50
        override fun setLiquidGlassStrengthPercent(percent: Int) = completedJob()
        override fun getLiquidGlassEnabled() = true
        override fun setLiquidGlassEnabled(enabled: Boolean) = completedJob()
        override fun getBottomTabLabelsVisible() = true
        override fun setBottomTabLabelsVisible(visible: Boolean) = completedJob()
        override fun saveStyle(style: StyleSettings) = completedJob()
        override fun getOcrConfusionReplacementMask() = 0
        override fun setOcrConfusionReplacementMask(mask: Int) = completedJob()
        override fun setUpdateError(error: String) = completedJob()
        override suspend fun getIgnoredUpdateVersion(): String? {
            readStarted?.complete(Unit)
            releaseRead?.await()
            return ignoredVersion
        }
        override suspend fun setIgnoredUpdateVersion(version: String) {
            writeFailure?.let { throw it }
            writeStarted?.complete(Unit)
            releaseWrite?.await()
            ignoredVersion = version
        }

        private fun completedJob() = Job().also { it.complete() }
    }
}
