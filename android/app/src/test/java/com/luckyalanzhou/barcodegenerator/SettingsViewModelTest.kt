package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.LegacySettingsMigrator
import com.luckyalanzhou.barcodegenerator.data.SettingsStore
import com.luckyalanzhou.barcodegenerator.domain.SettingsMigration
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
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
        val viewModel = SettingsViewModel(settingsRepository, settingsMigration)

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
}
