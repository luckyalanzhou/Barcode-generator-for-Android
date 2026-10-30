package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking
import com.luckyalanzhou.barcodegenerator.domain.awaitSettingsCommit
import com.luckyalanzhou.barcodegenerator.domain.UPDATE_FAILURE_SUMMARY
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreUpdateErrorTest {
    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.settingsDataStore.edit { preferences ->
            preferences.remove(SettingsStore.LAST_UPDATE_ERROR)
            preferences.remove(SettingsStore.SETTINGS_MIGRATED)
        }
        context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    @Test
    fun updateErrorWritesOnlyGenericSummary() = runBlocking {
        val store = SettingsStore(context)
        store.load()
        store.setUpdateError(SENSITIVE_DETAIL).awaitSettingsCommit()

        assertEquals(UPDATE_FAILURE_SUMMARY, store.get(SettingsStore.LAST_UPDATE_ERROR, ""))
    }

    @Test
    fun loadReplacesPreviouslyPersistedRawError() = runBlocking {
        context.settingsDataStore.edit { it[SettingsStore.LAST_UPDATE_ERROR] = SENSITIVE_DETAIL }
        val store = SettingsStore(context)

        store.load()

        assertEquals(UPDATE_FAILURE_SUMMARY, store.get(SettingsStore.LAST_UPDATE_ERROR, ""))
    }

    @Test
    fun legacyMigrationDoesNotPreserveRawErrorDetails() = runBlocking {
        context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE).edit()
            .putString("last_update_error", SENSITIVE_DETAIL)
            .commit()
        val store = SettingsStore(context)
        store.load()

        LegacySettingsMigrator(context, store).migrateIfNeeded()

        assertEquals(UPDATE_FAILURE_SUMMARY, store.get(SettingsStore.LAST_UPDATE_ERROR, ""))
    }

    private companion object {
        const val SENSITIVE_DETAIL = "Provider rejected content://private/path/update.apk"
    }
}
