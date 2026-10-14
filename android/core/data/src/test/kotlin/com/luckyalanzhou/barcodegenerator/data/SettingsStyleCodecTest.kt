package com.luckyalanzhou.barcodegenerator.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import org.junit.Assert.*
import org.junit.Test

class SettingsStyleCodecTest {
    @Test fun oldPreferencesPreserveDefaultsWithoutResettingExistingStyle() {
        assertEquals(StyleSettings(), decodeStyleSettings(emptyPreferences()))
        val old = mutablePreferencesOf(SettingsStore.COLOR_SCHEME to "dark", SettingsStore.TEXT_SIZE to 18f)
        val decoded = decodeStyleSettings(old)
        assertEquals("dark", decoded.colorScheme)
        assertEquals(18f, decoded.textSize, 0f)
        assertFalse(decoded.reduceMotion)
        assertFalse(decoded.enhanceContrast)
    }

    @Test fun everyAccessibilityCombinationRoundTripsAndSurvivesSliderUpdate() {
        for (motion in listOf(false, true)) for (contrast in listOf(false, true)) {
            val style = StyleSettings(textSize = 18f, colorScheme = "dark", reduceMotion = motion,
                enhanceContrast = contrast)
            val values = mutablePreferencesOf()
            values.writeStyleSettings(style)
            assertEquals(style, decodeStyleSettings(values))
            val updated = decodeStyleSettings(values).copy(barWidth = 250f)
            values.writeStyleSettings(updated)
            assertEquals(updated, decodeStyleSettings(values))
        }
    }

    @Test fun invalidLegacyDimensionsRemainClamped() {
        val values = mutablePreferencesOf(SettingsStore.TEXT_SIZE to 90f, SettingsStore.BAR_HEIGHT to -100,
            SettingsStore.BAR_WIDTH to 10000f, SettingsStore.MARGIN to -1)
        val decoded = decodeStyleSettings(values)
        assertEquals(24f, decoded.textSize, 0f)
        assertEquals(30, decoded.barHeight)
        assertEquals(300f, decoded.barWidth, 0f)
        assertEquals(0, decoded.margin)
    }

    @Test fun removedTransparencyPreferenceIsIgnoredAndCleanedOnSave() {
        val key = androidx.datastore.preferences.core.booleanPreferencesKey("appearance_reduce_transparency")
        val values = mutablePreferencesOf(key to true, SettingsStore.TEXT_SIZE to 18f)
        val style = decodeStyleSettings(values)
        assertEquals(18f, style.textSize, 0f)
        assertFalse(style.enhanceContrast)
        values.writeStyleSettings(style)
        assertNull(values[key])
        assertEquals(style, decodeStyleSettings(values))
    }
}
