package com.luckyalanzhou.barcodegenerator.presentation.shared

import android.app.Application
import android.os.Looper
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesQuerySession
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BarcodeItemViewModelLoggerFailureTest {
    @Test
    fun failedSearchRefreshDoesNotCrashWhenDiagnosticLoggerFails() = runBlocking {
        val searchGroupCalls = AtomicInteger()
        val failedRefreshEntered = CountDownLatch(1)
        val diagnosticAttempted = CountDownLatch(1)
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "searchFavoriteGroups" -> {
                    if (searchGroupCalls.incrementAndGet() > 1) {
                        failedRefreshEntered.countDown()
                        throw IOException("search storage unavailable")
                    }
                    emptyList<FavoriteGroup>()
                }
                "searchFavoriteItems" -> emptyList<CodeItem>()
                "saveItems" -> Unit
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val session = LibraryDataSession().apply {
            store.edit { items += CodeItem(1L, "123456", "Code 128-B") }
        }
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val querySession = FavoritesQuerySession(repository, session, persistence)
        querySession.search("123")
        val viewModel = BarcodeItemViewModel(
            dataSession = session,
            querySession = querySession,
            persistence = persistence,
            appLogger = AppLogger { _, _, _ ->
                diagnosticAttempted.countDown()
                throw IOException("diagnostic storage unavailable")
            },
        )

        val mainThread = Thread.currentThread()
        val previousUncaughtHandler = mainThread.uncaughtExceptionHandler
        val uncaughtFailure = AtomicReference<Throwable?>()
        mainThread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error ->
            uncaughtFailure.compareAndSet(null, error)
        }
        try {
            val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
            viewModel.updateBarcodeItem(1L, "12345678", "Code 128-B")
            val refreshDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (failedRefreshEntered.count > 0L && System.nanoTime() < refreshDeadline) {
                mainLooper.idle()
                failedRefreshEntered.await(5, TimeUnit.MILLISECONDS)
            }
            assertEquals("the post-mutation search refresh should run", 0L, failedRefreshEntered.count)
            val diagnosticDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (diagnosticAttempted.count > 0L && System.nanoTime() < diagnosticDeadline) {
                mainLooper.idle()
                Thread.sleep(5)
            }
            mainLooper.idle()
        } finally {
            mainThread.uncaughtExceptionHandler = previousUncaughtHandler
        }

        assertNull("a failed diagnostic write must not escape the ViewModel coroutine", uncaughtFailure.get())
        assertEquals(0L, diagnosticAttempted.count)
        assertEquals("12345678", session.store.itemsSnapshot().single().text)
    }
}
