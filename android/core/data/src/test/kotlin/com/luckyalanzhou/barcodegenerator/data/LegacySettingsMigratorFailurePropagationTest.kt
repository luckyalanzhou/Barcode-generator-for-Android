package com.luckyalanzhou.barcodegenerator.data

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LegacySettingsMigratorFailurePropagationTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val legacyPreferences = context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE)

    @After
    fun clearMigrationStores() {
        runBlocking {
            legacyPreferences.edit().clear().commit()
            context.settingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun fatalLegacyPreferenceReadPropagatesBeforeMigrationCompletesOrDeletesSource() = runBlocking {
        legacyPreferences.edit().putInt("style_bar_height", 72).commit()
        context.settingsDataStore.edit { it.clear() }
        val fatal = AssertionError("legacy preference invariant failed")
        val throwingPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, arguments ->
            if (method.name == "getInt" && arguments?.firstOrNull() == "style_bar_height") throw fatal
            method.invoke(legacyPreferences, *(arguments ?: emptyArray()))
        } as SharedPreferences
        val migrationContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "barcode_app") throwingPreferences else super.getSharedPreferences(name, mode)
        }
        val settingsStore = SettingsStore(context)
        settingsStore.load()

        val error = runCatching {
            LegacySettingsMigrator(migrationContext, settingsStore).migrateIfNeeded()
        }.exceptionOrNull()

        assertTrue(legacyPreferences.contains("style_bar_height"))
        assertFalse(settingsStore.get(SettingsStore.SETTINGS_MIGRATED, false))
        assertSame(fatal, error)
    }

    @Test
    fun invalidLegacyPreferenceTypesUseDefaultsAndStillCompleteMigration() = runBlocking {
        legacyPreferences.edit()
            .putString("style_show_text", "invalid")
            .putInt("style_text_position", 1)
            .putString("style_text_size", "invalid")
            .putString("style_bar_height", "invalid")
            .putString("style_bar_width", "invalid")
            .putString("style_margin", "invalid")
            .putString("style_show_format", "invalid")
            .putInt("style_color_scheme", 1)
            .commit()
        context.settingsDataStore.edit { it.clear() }
        val settingsStore = SettingsStore(context)
        settingsStore.load()

        val migrated = LegacySettingsMigrator(context, settingsStore).migrateIfNeeded()

        assertEquals(StyleSettings(), migrated)
        assertEquals(StyleSettings(), settingsStore.loadStyle())
        assertTrue(settingsStore.get(SettingsStore.SETTINGS_MIGRATED, false))
        listOf(
            "style_show_text", "style_text_position", "style_text_size", "style_bar_height",
            "style_bar_width", "style_margin", "style_show_format", "style_color_scheme",
        ).forEach { key -> assertFalse("legacy key should be cleared after fallback: $key", legacyPreferences.contains(key)) }
    }
}
