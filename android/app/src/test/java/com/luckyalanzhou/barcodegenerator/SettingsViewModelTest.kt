package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.LegacySettingsMigrator
import com.luckyalanzhou.barcodegenerator.data.SettingsStore
import com.luckyalanzhou.barcodegenerator.domain.SettingsMigration
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun loadPersistedStatePerformsPersistenceWorkOffCallerThread() = runBlocking {
        val callerThread = Thread.currentThread()
        var repositoryLoadThread: Thread? = null
        var migrationThread: Thread? = null
        val settingsRepository = object : SettingsRepository {
            override suspend fun load() {
                repositoryLoadThread = Thread.currentThread()
            }

            override fun loadStyle() = StyleSettings(colorScheme = "dark", showFormat = true)

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
        assertTrue(viewModel.uiState.value.showFormat)
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
            StyleSettings(showFormat = true, textSize = 12f, barHeight = 50, barWidth = 200f, margin = 3),
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
        assertTrue(viewModel.uiState.value.showFormat)
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
        val style = StyleSettings(textSize = 18f, barWidth = 260f, colorScheme = "dark",
            reduceMotion = true, enhanceContrast = true)
        viewModel.initialize(StyleSettings(), 0)

        viewModel.updateStyle(style)
        style.textSize = 10f
        style.barWidth = 100f

        assertEquals(18f, viewModel.style.textSize, 0f)
        assertEquals(260f, viewModel.style.barWidth, 0f)
        assertEquals("dark", viewModel.uiState.value.scheme)
        assertTrue(viewModel.uiState.value.style.reduceMotion)
        assertTrue(viewModel.uiState.value.style.enhanceContrast)
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
}
