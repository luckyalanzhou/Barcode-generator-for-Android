package com.luckyalanzhou.barcodegenerator.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.edit
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.domain.awaitSettingsCommit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsStoreCorruptionRecoveryTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun clearSettings() = runBlocking {
        context.settingsDataStore.edit { it.clear() }
        Unit
    }

    @Test
    fun corruptPreferencesRecoverToDefaultsAndAcceptFutureWrites() = runBlocking {
        val settingsFile = context.preferencesDataStoreFile("barcode_settings")
        settingsFile.parentFile?.mkdirs()
        settingsFile.writeBytes("invalid preferences proto".toByteArray())

        val recoveredStore = SettingsStore(context)
        recoveredStore.load()

        assertEquals(StyleSettings(), recoveredStore.loadStyle())

        val updatedStyle = StyleSettings(colorScheme = "dark", barHeight = 61)
        recoveredStore.saveStyle(updatedStyle).awaitSettingsCommit()

        val reloadedStore = SettingsStore(context)
        reloadedStore.load()
        assertEquals(updatedStyle, reloadedStore.loadStyle())
    }
}
