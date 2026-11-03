package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreUpdateIgnoredVersionTest {
    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.settingsDataStore.edit { it.remove(SettingsStore.IGNORED_UPDATE_VERSION) }
        Unit
    }

    @Test
    fun newStoreHasNoIgnoredUpdateVersion() = runBlocking {
        assertEquals(null, SettingsStore(context).getIgnoredUpdateVersion())
    }

    @Test
    fun ignoredVersionPersistsWhenStoreIsRecreated() = runBlocking {
        SettingsStore(context).setIgnoredUpdateVersion("1.2.3")

        assertEquals("1.2.3", SettingsStore(context).getIgnoredUpdateVersion())
    }
}
