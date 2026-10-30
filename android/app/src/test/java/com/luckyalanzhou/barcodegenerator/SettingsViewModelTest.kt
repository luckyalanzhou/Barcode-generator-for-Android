package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.LegacySettingsMigrator
import com.luckyalanzhou.barcodegenerator.data.SettingsStore
import com.luckyalanzhou.barcodegenerator.domain.SettingsMigration
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun loadedSettingsArePublishedWhenDiagnosticLoggingFails() = runBlocking {
        val repository = object : SettingsRepository {
            override suspend fun load() = Unit
            override fun loadStyle() = StyleSettings(colorScheme = "dark", textSize = 19f)
            override fun saveStyle(style: StyleSettings) = completedJob()
            override fun getOcrConfusionReplacementMask() = 6
            override fun setOcrConfusionReplacementMask(mask: Int) = completedJob()
            override fun setUpdateError(error: String) = completedJob()
        }
        val migration = object : SettingsMigration {
            override suspend fun migrateIfNeeded(): StyleSettings? = null
        }
        val viewModel = SettingsViewModel(
            repository,
            migration,
            AppLogger { _, _, _ -> error("diagnostic storage unavailable") },
        )

        viewModel.loadPersistedState()

        assertEquals("dark", viewModel.uiState.value.scheme)
        assertEquals(19f, viewModel.uiState.value.textSize, 0f)
        assertEquals(6, viewModel.uiState.value.ocrMask)
    }

    @Test
    fun writeStillStartsWhenStartDiagnosticLoggingFails() {
        val pending = Job()
        var writes = 0
        val repository = object : SettingsRepository {
            override suspend fun load() = Unit
            override fun loadStyle() = StyleSettings()
            override fun saveStyle(style: StyleSettings) = pending.also { writes++ }
            override fun getOcrConfusionReplacementMask() = 0
            override fun setOcrConfusionReplacementMask(mask: Int) = completedJob()
            override fun setUpdateError(error: String) = completedJob()
        }
        val migration = object : SettingsMigration {
            override suspend fun migrateIfNeeded(): StyleSettings? = null
        }
        val viewModel = SettingsViewModel(
            repository,
            migration,
            AppLogger { _, message, _ ->
                if (message.contains("save start")) error("diagnostic storage unavailable")
            },
        )

        viewModel.save()

        assertEquals(1, writes)
        pending.complete()
        assertEquals(false, viewModel.uiState.value.saveFailed)
    }

    @Test
    fun failedWritePublishesFailureAndNoticeWhenFailureDiagnosticLoggingFails() = runBlocking {
        val pending = Job()
        val failure = IllegalStateException("settings write failed")
        val diagnosticFailure = IllegalStateException("diagnostic storage unavailable")
        val repository = object : SettingsRepository {
            override suspend fun load() = Unit
            override fun loadStyle() = StyleSettings()
            override fun saveStyle(style: StyleSettings) = pending
            override fun getOcrConfusionReplacementMask() = 0
            override fun setOcrConfusionReplacementMask(mask: Int) = completedJob()
            override fun setUpdateError(error: String) = completedJob()
        }
        val migration = object : SettingsMigration {
            override suspend fun migrateIfNeeded(): StyleSettings? = null
        }
        val viewModel = SettingsViewModel(
            repository,
            migration,
            AppLogger { _, message, _ ->
                if (message.contains("save failed")) throw diagnosticFailure
            },
        )
        viewModel.save()

        pending.completeExceptionally(failure)

        assertTrue(viewModel.uiState.value.saveFailed)
        viewModel.persistenceFailures.first()
        assertSame(diagnosticFailure, failure.suppressed.single())
    }

    @Test
    fun userEditMadeWhileInitialLoadIsSuspendedIsNotOverwrittenByLoadedSettings() = runBlocking {
        val loadStarted = CompletableDeferred<Unit>()
        val finishLoad = CompletableDeferred<Unit>()
        val savedStyles = mutableListOf<StyleSettings>()
        val savedOcrMasks = mutableListOf<Int>()
        val repository = object : SettingsRepository {
            override suspend fun load() {
                loadStarted.complete(Unit)
                finishLoad.await()
            }

            override fun loadStyle() = StyleSettings(colorScheme = "dark", textSize = 14f, barWidth = 270f, showText = false)
            override fun saveStyle(style: StyleSettings) = completedJob().also { savedStyles += style.copy() }
            override fun getOcrConfusionReplacementMask() = 0
            override fun setOcrConfusionReplacementMask(mask: Int) = completedJob().also { savedOcrMasks += mask }
            override fun setUpdateError(error: String) = completedJob()
        }
        val migration = object : SettingsMigration {
            override suspend fun migrateIfNeeded(): StyleSettings? = null
        }
        val viewModel = SettingsViewModel(repository, migration, AppLogger { _, _, _ -> })
        val loading = async { viewModel.loadPersistedState() }

        loadStarted.await()
        viewModel.updateStyle(StyleSettings(colorScheme = "light", textSize = 18f))
        viewModel.save()
        viewModel.setOcrMaskPersisted(7)
        finishLoad.complete(Unit)
        loading.await()

        assertEquals("light", viewModel.uiState.value.style.colorScheme)
        assertEquals(18f, viewModel.uiState.value.style.textSize, 0f)
        assertEquals("loaded values not edited by the user must be preserved", 270f, viewModel.style.barWidth, 0f)
        assertEquals(false, viewModel.style.showText)
        assertEquals("the merged user choice must be persisted after initialization", "light", savedStyles.last().colorScheme)
        assertEquals(18f, savedStyles.last().textSize, 0f)
        assertEquals(7, viewModel.uiState.value.ocrMask)
        assertEquals(7, savedOcrMasks.last())
    }

    @Test
    fun loadPersistedStatePerformsPersistenceWorkOffCallerThread() = runBlocking {
        val callerThread = Thread.currentThread()
        var repositoryLoadThread: Thread? = null
        var migrationThread: Thread? = null
        val settingsRepository = object : SettingsRepository {
            override suspend fun load() {
                repositoryLoadThread = Thread.currentThread()
            }

            override fun loadStyle() = StyleSettings(colorScheme = "dark")

            override fun saveStyle(style: StyleSettings) = completedJob()

            override fun getOcrConfusionReplacementMask() = 7

            override fun setOcrConfusionReplacementMask(mask: Int) = completedJob()

            override fun setUpdateError(error: String) = completedJob()
        }
        val settingsMigration = object : SettingsMigration {
            override suspend fun migrateIfNeeded(): StyleSettings? {
                migrationThread = Thread.currentThread()
                return null
            }
        }
        val viewModel = SettingsViewModel(settingsRepository, settingsMigration, AppLogger { _, _, _ -> })

        viewModel.loadPersistedState()

        assertNotSame(callerThread, repositoryLoadThread)
        assertNotSame(callerThread, migrationThread)
        assertEquals("dark", viewModel.uiState.value.scheme)
        assertEquals(7, viewModel.uiState.value.ocrMask)
    }

    @Test
    fun sliderChangesUpdateTheOwnedStyleAndUiSnapshot() {
        val settingsStore = SettingsStore(android.app.Application())
        val viewModel = SettingsViewModel(
            settingsStore,
            LegacySettingsMigrator(android.app.Application(), settingsStore),
            AppLogger { _, _, _ -> },
        )
        viewModel.initialize(
            StyleSettings(textSize = 12f, barHeight = 50, barWidth = 200f, margin = 3),
            ocrMask = 5,
        )

        viewModel.setTextSize(16f)
        viewModel.setBarHeight(61.8f)
        viewModel.setBarWidth(240f)
        viewModel.setMargin(8.9f)

        assertEquals(16f, viewModel.style.textSize, 0f)
        assertEquals(61, viewModel.style.barHeight)
        assertEquals(240f, viewModel.style.barWidth, 0f)
        assertEquals(8, viewModel.style.margin)
        assertEquals(16f, viewModel.uiState.value.textSize, 0f)
        assertEquals(61f, viewModel.uiState.value.barHeight, 0f)
        assertEquals(240f, viewModel.uiState.value.barWidth, 0f)
        assertEquals(8f, viewModel.uiState.value.margin, 0f)
        assertEquals(5, viewModel.uiState.value.ocrMask)
    }

    @Test
    fun updateStyleReplacesTheSnapshotWithoutMutatingTheInputAfterwards() {
        val settingsStore = SettingsStore(android.app.Application())
        val viewModel = SettingsViewModel(
            settingsStore,
            LegacySettingsMigrator(android.app.Application(), settingsStore),
            AppLogger { _, _, _ -> },
        )
        val style = StyleSettings(textSize = 18f, barWidth = 260f, colorScheme = "dark")
        viewModel.initialize(StyleSettings(), 0)

        viewModel.updateStyle(style)
        style.textSize = 10f
        style.barWidth = 100f

        assertEquals(18f, viewModel.style.textSize, 0f)
        assertEquals(260f, viewModel.style.barWidth, 0f)
        assertEquals("dark", viewModel.uiState.value.scheme)
    }

    private fun completedJob() = Job().also { it.complete() }

    @Test
    fun writeLogsCompletionInsteadOfPretendingAnActiveJobSucceeded() {
        val entries = mutableListOf<Pair<String, Throwable?>>()
        var pending = Job()
        val repository = object : SettingsRepository {
            override suspend fun load() = Unit
            override fun loadStyle() = StyleSettings()
            override fun saveStyle(style: StyleSettings) = pending
            override fun getOcrConfusionReplacementMask() = 0
            override fun setOcrConfusionReplacementMask(mask: Int) = pending
            override fun setUpdateError(error: String) = pending
        }
        val migration = object : SettingsMigration {
            override suspend fun migrateIfNeeded(): StyleSettings? = null
        }
        val model = SettingsViewModel(repository, migration, AppLogger { _, message, error -> entries.add(message to error) })
        model.save()
        assertEquals(1, entries.size)
        assertTrue(entries.last().first.startsWith("save start"))
        pending.complete()
        assertTrue(entries.last().first.startsWith("save success"))
        pending = Job()
        model.setOcrMaskPersisted(2)
        pending.cancel()
        assertTrue(entries.last().first.startsWith("ocr_mask_save cancelled"))
        assertEquals(null, entries.last().second)
        pending = Job()
        model.save()
        val failure = IllegalStateException("write failed")
        pending.completeExceptionally(failure)
        assertTrue(entries.last().first.startsWith("save failed"))
        assertEquals(failure, entries.last().second)
        assertTrue(model.uiState.value.saveFailed)
        pending = Job()
        model.save()
        pending.complete()
        assertEquals(false, model.uiState.value.saveFailed)
    }

    @Test
    fun ocrMaskReadUsesLatestSelectionWhilePersistenceIsPending() {
        var storedMask = 0
        val pendingWrite = Job()
        val repository = object : SettingsRepository {
            override suspend fun load() = Unit
            override fun loadStyle() = StyleSettings()
            override fun saveStyle(style: StyleSettings) = completedJob()
            override fun getOcrConfusionReplacementMask() = storedMask
            override fun setOcrConfusionReplacementMask(mask: Int) = pendingWrite
            override fun setUpdateError(error: String) = completedJob()
        }
        val migration = object : SettingsMigration {
            override suspend fun migrateIfNeeded(): StyleSettings? = null
        }
        val model = SettingsViewModel(repository, migration, AppLogger { _, _, _ -> })
        model.initialize(StyleSettings(), storedMask)

        model.setOcrMaskPersisted(7)

        assertEquals("recognition should use the latest UI selection before DataStore finishes", 7, model.getOcrMask())
        assertEquals("the persisted cache must remain unchanged until the write completes", 0, repository.getOcrConfusionReplacementMask())
        pendingWrite.complete()
        storedMask = 7
        assertEquals(7, model.getOcrMask())
    }
}
