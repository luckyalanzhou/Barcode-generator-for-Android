package com.luckyalanzhou.barcodegenerator.data

import android.app.Application
import android.content.Context
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class LegacyBarcodeDataMigratorFailurePropagationTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE)

    @After
    fun clearLegacyPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun migrationCancellationPropagatesAndRetainsLegacyPreferences() = runBlocking {
        val cancellation = CancellationException("migration cancelled")
        val error = attemptMigration(cancellation)

        assertSame(cancellation, error)
        assertTrue(preferences.contains("items"))
        assertFalse(preferences.getBoolean("room_data_migrated", false))
    }

    @Test
    fun fatalErrorPropagatesAndRetainsLegacyPreferences() = runBlocking {
        val fatal = AssertionError("migration invariant failed")
        val error = attemptMigration(fatal)

        assertSame(fatal, error)
        assertTrue(preferences.contains("items"))
        assertFalse(preferences.getBoolean("room_data_migrated", false))
    }

    private suspend fun attemptMigration(failure: Throwable): Throwable? {
        preferences.edit().clear().putString("items", "[]").commit()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            check(method.name == "migrateLegacyDataIfNeeded") {
                "Unexpected repository operation: ${method.name}"
            }
            throw failure
        } as BarcodeRepository

        return runCatching { LegacyBarcodeDataMigrator(context, repository).migrateIfNeeded() }
            .exceptionOrNull()
    }
}
