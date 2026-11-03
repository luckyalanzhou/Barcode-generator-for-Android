package com.luckyalanzhou.barcodegenerator.data

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
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

    @Test
    fun repositoryFailurePropagatesWithoutPersistingSkippedMigrationState() = runBlocking {
        val failure = IllegalStateException("temporary Room failure")
        val error = attemptMigration(failure)

        assertSame(failure, error)
        assertTrue("legacy data must remain available for retry", preferences.contains("items"))
        assertFalse("a repository error is not the same as Room already having data", preferences.getBoolean("room_data_migration_skipped", false))
        assertFalse(preferences.getBoolean("room_data_migrated", false))
    }

    @Test
    fun migrationRunsLegacyDatabaseWorkOffMainThread() {
        preferences.edit().clear().putString("items", "[]").commit()
        val repositoryThread = AtomicReference<Thread>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            check(method.name == "migrateLegacyDataIfNeeded")
            repositoryThread.set(Thread.currentThread())
            true
        } as BarcodeRepository

        val mainLooper = Looper.getMainLooper()
        var migrationFailure: Throwable? = null
        Handler(mainLooper).post {
            migrationFailure = runCatching {
                runBlocking { LegacyBarcodeDataMigrator(context, repository).migrateIfNeeded() }
            }.exceptionOrNull()
        }
        shadowOf(mainLooper).idle()

        assertNull(migrationFailure)
        assertNotSame("legacy migration must not inherit the UI thread for blocking preference I/O", Looper.getMainLooper().thread, repositoryThread.get())
    }

    @Test
    fun failureToPersistSkippedMigrationStatePropagatesToTheCaller() = runBlocking {
        val values = linkedMapOf<String, Any>("items" to "[]")
        val fakePreferences = preferences(values, failingBooleanCommitKey = "room_data_migration_skipped")
        val fakeContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = fakePreferences
        }
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            check(method.name == "migrateLegacyDataIfNeeded")
            false
        } as BarcodeRepository

        val error = runCatching { LegacyBarcodeDataMigrator(fakeContext, repository).migrateIfNeeded() }
            .exceptionOrNull()

        assertTrue("startup must not proceed as though an unpersisted migration skip were durable", error is IllegalStateException)
        assertTrue("failed marker persistence must preserve legacy data", values.containsKey("items"))
    }

    @Test
    fun failureToPersistCompletedMigrationStatePropagatesToTheCaller() = runBlocking {
        val values = linkedMapOf<String, Any>("items" to "[]")
        val fakePreferences = preferences(values, failingBooleanCommitKey = "room_data_migrated")
        val fakeContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = fakePreferences
        }
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            check(method.name == "migrateLegacyDataIfNeeded")
            true
        } as BarcodeRepository

        val error = runCatching { LegacyBarcodeDataMigrator(fakeContext, repository).migrateIfNeeded() }
            .exceptionOrNull()

        assertTrue("startup must report a non-durable migration commit", error is IllegalStateException)
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

    private fun preferences(values: MutableMap<String, Any>, failingBooleanCommitKey: String): SharedPreferences =
        Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, arguments ->
            val key = arguments?.firstOrNull() as? String
            when (method.name) {
                "contains" -> key in values
                "getBoolean" -> values[key] as? Boolean ?: arguments?.get(1) as Boolean
                "getString" -> values[key] as? String ?: arguments?.get(1) as String?
                "getStringSet" -> values[key] as? Set<String> ?: arguments?.get(1) as? Set<String>
                "edit" -> preferenceEditor(values, failingBooleanCommitKey)
                else -> error("Unexpected SharedPreferences operation: ${method.name}")
            }
        } as SharedPreferences

    private fun preferenceEditor(values: MutableMap<String, Any>, failingBooleanCommitKey: String): SharedPreferences.Editor {
        val writes = linkedMapOf<String, Any>()
        val removals = linkedSetOf<String>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, arguments ->
            val key = arguments?.firstOrNull() as? String
            when (method.name) {
                "putBoolean", "putString", "putStringSet", "putInt", "putLong", "putFloat" -> {
                    if (key != null) {
                        writes[key] = arguments?.get(1) ?: return@newProxyInstance editor
                        removals.remove(key)
                    }
                    editor
                }
                "remove" -> {
                    if (key != null) {
                        writes.remove(key)
                        removals += key
                    }
                    editor
                }
                "clear" -> {
                    values.clear()
                    writes.clear()
                    removals.clear()
                    editor
                }
                "commit" -> {
                    if (writes[failingBooleanCommitKey] == true) false else {
                        removals.forEach(values::remove)
                        values.putAll(writes)
                        true
                    }
                }
                "apply" -> {
                    removals.forEach(values::remove)
                    values.putAll(writes)
                    Unit
                }
                else -> error("Unexpected SharedPreferences.Editor operation: ${method.name}")
            }
        } as SharedPreferences.Editor
        return editor
    }
}
