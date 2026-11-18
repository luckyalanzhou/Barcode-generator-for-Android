package com.luckyalanzhou.barcodegenerator.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsStoreLiquidGlassStrengthTest {
    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.settingsDataStore.edit { it.clear() }
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        context.settingsDataStore.edit { it.clear() }
        Unit
    }

    @Test
    fun missingStrengthDefaultsToFiftyPercent() = runBlocking {
        val store = SettingsStore(context)
        store.load()

        assertEquals(50, store.getLiquidGlassStrengthPercent())
    }

    @Test
    fun strengthPersistsWhenStoreIsRecreated() = runBlocking {
        val store = SettingsStore(context)
        store.load()
        store.setLiquidGlassStrengthPercent(73).join()

        val reloaded = SettingsStore(context)
        reloaded.load()
        assertEquals(73, reloaded.getLiquidGlassStrengthPercent())
    }

    @Test
    fun writesClampStrengthToZeroThroughOneHundred() = runBlocking {
        val store = SettingsStore(context)
        store.load()

        store.setLiquidGlassStrengthPercent(-8).join()
        assertEquals(0, SettingsStore(context).also { it.load() }.getLiquidGlassStrengthPercent())
        store.setLiquidGlassStrengthPercent(108).join()
        assertEquals(100, SettingsStore(context).also { it.load() }.getLiquidGlassStrengthPercent())
    }

    @Test
    fun rapidWritesAreSerializedAndLastValueRemainsStored() = runBlocking {
        val store = SettingsStore(context)
        store.load()
        val first = store.setLiquidGlassStrengthPercent(21)
        val second = store.setLiquidGlassStrengthPercent(84)
        first.join()
        second.join()

        assertEquals(84, SettingsStore(context).also { it.load() }.getLiquidGlassStrengthPercent())
    }

    @Test
    fun switchingLiquidGlassOffAndOnDoesNotChangeStrength() = runBlocking {
        val store = SettingsStore(context)
        store.load()
        store.setLiquidGlassStrengthPercent(67).join()
        store.setLiquidGlassEnabled(false).join()
        store.setLiquidGlassEnabled(true).join()

        assertEquals(67, SettingsStore(context).also { it.load() }.getLiquidGlassStrengthPercent())
    }
}
