package com.luckyalanzhou.barcodegenerator.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsStoreRemovedPreferencesTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val showFormatKey = booleanPreferencesKey("style_show_format")
    private val reduceMotionKey = booleanPreferencesKey("appearance_reduce_motion")
    private val enhanceContrastKey = booleanPreferencesKey("appearance_enhance_contrast")

    @After
    fun clearSettings() = runBlocking {
        context.settingsDataStore.edit { it.clear() }
        Unit
    }

    @Test
    fun loadingRemovesRemovedStylePreferencesAndKeepsBarcodeStyle() = runBlocking {
        context.settingsDataStore.edit { preferences ->
            preferences.clear()
            preferences[showFormatKey] = true
            preferences[reduceMotionKey] = true
            preferences[enhanceContrastKey] = true
            preferences[SettingsStore.TEXT_SIZE] = 18f
            preferences[SettingsStore.COLOR_SCHEME] = "dark"
        }

        val store = SettingsStore(context)
        store.load()

        val persisted = context.settingsDataStore.data.first()
        assertFalse(persisted.contains(showFormatKey))
        assertFalse(persisted.contains(reduceMotionKey))
        assertFalse(persisted.contains(enhanceContrastKey))
        assertEquals(18f, store.loadStyle().textSize, 0f)
        assertEquals("dark", store.loadStyle().colorScheme)
    }
}
