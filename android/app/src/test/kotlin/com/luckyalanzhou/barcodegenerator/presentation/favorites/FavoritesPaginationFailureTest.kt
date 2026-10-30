package com.luckyalanzhou.barcodegenerator.presentation.favorites

import android.app.Application
import android.os.Looper
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryLoadMetadata
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FavoritesPaginationFailureTest {
    @Test
    fun pageReadFailureIsLoggedInsteadOfEscapingTheViewModelScope() {
        val expectedFailure = IOException("收藏分页读取失败")
        val requestStarted = CountDownLatch(1)
        val failureLogged = CountDownLatch(1)
        val diagnosticFailure = IllegalStateException("diagnostic storage unavailable")
        val messages = CopyOnWriteArrayList<String>()
        val loggedErrors = CopyOnWriteArrayList<Throwable>()
        val pageCalls = AtomicInteger()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "loadFavoriteGroupPage" -> {
                    if (pageCalls.incrementAndGet() == 1) {
                        requestStarted.countDown()
                        throw expectedFailure
                    }
                    emptyList<FavoriteGroup>()
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession()
        val cursorGroup = FavoriteGroup(7L, "一级", "已加载", 10L, mutableListOf())
        dataSession.store.edit { groups += cursorGroup }
        val logger = AppLogger { tag, message, error ->
            messages += "$tag:$message:${error?.message}"
            if (error != null) loggedErrors += error
            if (tag == "favorites" && error != null) {
                failureLogged.countDown()
                throw diagnosticFailure
            }
        }
        val querySession = FavoritesQuerySession(repository, dataSession, persistence)
        val backupRepository = object : FavoritesBackupRepository {
            override suspend fun export(output: OutputStream) = error("Unexpected export")
            override fun restore(bytes: ByteArray) = error("Unexpected restore")
            override suspend fun inspectImport(backup: com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup) =
                error("Unexpected import inspection")
            override suspend fun import(
                backup: com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup,
                overwriteConflicts: Boolean,
            ) = error("Unexpected import")
        }
        val viewModel = FavoritesViewModel(
            dataSession = dataSession,
            querySession = querySession,
            barcodeDataCoordinator = BarcodeDataCoordinator(repository, backupRepository, persistence),
            libraryDataCoordinator = LibraryDataCoordinator(persistence, dataSession, logger),
            persistence = persistence,
            appLogger = logger,
        )

        kotlinx.coroutines.runBlocking {
            querySession.onLibrarySnapshotLoaded(
                LibraryLoadMetadata(
                    generation = 1L,
                    lastFavoriteGroupCursor = FavoriteGroupPageCursor(cursorGroup.savedAt, cursorGroup.id),
                    hasMoreFavoriteGroups = true,
                ),
            )
        }

        val uncaught = CopyOnWriteArrayList<Throwable>()
        val mainThread = Looper.getMainLooper().thread
        val previousHandler = mainThread.uncaughtExceptionHandler
        val previousDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        mainThread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> uncaught += error }
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
        try {
            viewModel.loadMoreFavoriteGroups("")
            val mainLooper = shadowOf(Looper.getMainLooper())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (requestStarted.count > 0 && System.nanoTime() < deadline) {
                mainLooper.idle()
                Thread.sleep(5)
            }
            assertEquals("the scroll callback must reach the repository page read", 0L, requestStarted.count)
            while (failureLogged.count > 0 && uncaught.isEmpty() && System.nanoTime() < deadline) {
                mainLooper.idle()
                Thread.sleep(5)
            }
            mainLooper.idle()
        } finally {
            mainThread.uncaughtExceptionHandler = previousHandler
            Thread.setDefaultUncaughtExceptionHandler(previousDefaultHandler)
        }

        assertTrue("ordinary paging failure escaped as an uncaught coroutine error: $uncaught", uncaught.isEmpty())
        assertTrue("ordinary paging failures must be recorded", failureLogged.count == 0L)
        assertTrue("pagination failure log missing from $messages", messages.any { "pagination_load_failed" in it })
        assertTrue("the caught repository exception should be attached to the diagnostic", loggedErrors.isNotEmpty())
        val recordedFailure = loggedErrors.single()
        assertTrue(generateSequence(recordedFailure) { it.cause }.any { it === expectedFailure })
        assertSame(diagnosticFailure, recordedFailure.suppressed.single())
        assertTrue("the failed page should expose an inline retry state", viewModel.searchStatus.value.paginationFailed)

        viewModel.loadMoreFavoriteGroups("")
        val retryLooper = shadowOf(Looper.getMainLooper())
        val retryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while ((pageCalls.get() < 2 || viewModel.searchStatus.value.paginationFailed) && System.nanoTime() < retryDeadline) {
            retryLooper.idle()
            Thread.sleep(5)
        }
        retryLooper.idle()
        assertEquals("retry should request the page again", 2, pageCalls.get())
        assertTrue("a successful retry should clear the inline error", !viewModel.searchStatus.value.paginationFailed)
    }
}
