package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.LegacySettingsMigrator
import com.luckyalanzhou.barcodegenerator.data.SettingsStore
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
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
}
