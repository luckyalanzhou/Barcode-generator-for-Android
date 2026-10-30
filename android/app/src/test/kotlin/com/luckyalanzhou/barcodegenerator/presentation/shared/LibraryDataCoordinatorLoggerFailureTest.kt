package com.luckyalanzhou.barcodegenerator.presentation.shared

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StartupBarcodeSnapshot
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LibraryDataCoordinatorLoggerFailureTest {
    @Test
    fun successfulSnapshotIsPublishedWhenCompletionLogFails() = runBlocking {
        val persistedItem = CodeItem(7L, "persisted", "Code 128-B", inHistory = true)
        val session = LibraryDataSession()
        val persistence = BarcodePersistenceCoordinator(
            repository(startupSnapshot = StartupBarcodeSnapshot(items = listOf(persistedItem), groups = emptyList(), links = emptyList(), folders = emptyList(), hasMoreGroups = false)),
            noLegacyMigration(),
        )
        val coordinator = LibraryDataCoordinator(
            persistence = persistence,
            session = session,
            logger = AppLogger { _, _, _ -> throw IOException("diagnostic storage unavailable") },
        )

        val failure = runCatching { coordinator.loadPersistedData() }.exceptionOrNull()

        assertNull("a diagnostic write failure must not abort publishing loaded data", failure)
        assertEquals(listOf(persistedItem.copy(folder = "")), session.dataState.value.items)
        assertTrue(session.dataState.value.isReady)
        assertFalse(session.dataState.value.loadFailed)
        assertEquals(1L, session.loadMetadata.value?.generation)
    }

    @Test
    fun failedSnapshotRetryDoesNotPublishPartialDataWhenRetryLogFails() = runBlocking {
        val previousItem = CodeItem(2L, "previous", "Code 128-B", inHistory = true)
        val startupLoadCount = AtomicInteger()
        val session = LibraryDataSession().apply {
            store.replace(listOf(previousItem), emptyList(), emptyList())
            publishDataState(isReady = true)
        }
        val persistence = BarcodePersistenceCoordinator(
            repository(
                startupSnapshot = StartupBarcodeSnapshot(
                    items = emptyList(),
                    groups = emptyList(),
                    links = emptyList(),
                    folders = emptyList(),
                    hasMoreGroups = false,
                ),
                loadFailure = IOException("database unavailable"),
                startupLoadCount = startupLoadCount,
            ),
            noLegacyMigration(),
        )
        val coordinator = LibraryDataCoordinator(
            persistence = persistence,
            session = session,
            logger = logger(),
        )

        val failure = runCatching { coordinator.loadPersistedData() }.exceptionOrNull()

        assertTrue("the original persistence failure must remain visible", failure?.cause is IOException)
        assertEquals("a failed snapshot is retried once", 2, startupLoadCount.get())
        assertEquals("no partial snapshot replaces the previous state", listOf(previousItem), session.dataState.value.items)
        assertFalse(session.dataState.value.isReady)
        assertTrue(session.dataState.value.loadFailed)
        assertNull(session.loadMetadata.value)
    }

    @Test
    fun writeFailureIsReportedAndPersistedSnapshotIsRestoredWhenLogsFail() {
        val persistedItem = CodeItem(4L, "persisted", "Code 128-B", inHistory = true)
        val optimisticItem = CodeItem(4L, "optimistic", "Code 128-B", inHistory = true)
        val session = LibraryDataSession().apply {
            store.replace(listOf(optimisticItem), emptyList(), emptyList())
            publishDataState(isReady = true)
        }
        val persistence = BarcodePersistenceCoordinator(
            repository(
                startupSnapshot = StartupBarcodeSnapshot(
                    items = listOf(persistedItem),
                    groups = emptyList(),
                    links = emptyList(),
                    folders = emptyList(),
                    hasMoreGroups = false,
                ),
                saveFailure = IOException("database unavailable"),
            ),
            noLegacyMigration(),
        )
        val viewModel = LibraryDataViewModel(
            coordinator = LibraryDataCoordinator(persistence, session, logger()),
            persistence = persistence,
            logger = logger(),
        )
        val viewModelStore = ViewModelStore().apply { put("library-data", viewModel) }
        val failureNotices = AtomicInteger()
        val noticeCollector = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).launch {
            viewModel.persistenceFailures.collect { failureNotices.incrementAndGet() }
        }

        try {
            val writeResult = runBlocking { persistence.persistItems(listOf(optimisticItem)).await() }
            assertTrue("the repository write should fail in this scenario", writeResult.isFailure)

            pumpMainUntil("persistence failure notice and snapshot recovery") {
                failureNotices.get() > 0 &&
                    session.dataState.value.isReady &&
                    session.dataState.value.items == listOf(persistedItem.copy(folder = ""))
            }

            assertEquals("each failed write should emit one user-facing notice", 1, failureNotices.get())
            assertFalse("the optimistic item must be replaced by persisted data", session.dataState.value.items.contains(optimisticItem))
        } finally {
            noticeCollector.cancel()
            viewModelStore.clear()
        }
    }

    @Test
    fun writeFailureRecoveryDoesNotOverwriteANewerCommittedSnapshot() {
        val oldItem = CodeItem(1L, "persisted-before-mutation", "Code 128-B", inHistory = true)
        val newItem = CodeItem(2L, "committed-during-recovery", "Code 128-B", inHistory = true)
        val oldSnapshot = StartupBarcodeSnapshot(
            items = listOf(oldItem),
            groups = emptyList(),
            links = emptyList(),
            folders = emptyList(),
            hasMoreGroups = false,
        )
        val newSnapshot = oldSnapshot.copy(items = listOf(newItem))
        val persistedSnapshot = AtomicReference(oldSnapshot)
        val loadCount = AtomicInteger()
        val recoveryReadStarted = CountDownLatch(1)
        val allowRecoveryReadToFinish = CountDownLatch(1)
        val mutationAttempted = CountDownLatch(1)
        val mutationFinished = CountDownLatch(1)
        val coordinationFailure = AtomicReference<Throwable?>()
        val session = LibraryDataSession().apply {
            store.replace(listOf(oldItem), emptyList(), emptyList())
            publishDataState(isReady = true)
        }
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "saveItems" -> throw IOException("write failed")
                "loadStartupSnapshot" -> {
                    val capturedSnapshot = persistedSnapshot.get()
                    if (loadCount.incrementAndGet() == 1) {
                        recoveryReadStarted.countDown()
                        check(allowRecoveryReadToFinish.await(5, TimeUnit.SECONDS))
                    }
                    capturedSnapshot
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, noLegacyMigration())
        val viewModel = LibraryDataViewModel(
            coordinator = LibraryDataCoordinator(persistence, session, logger()),
            persistence = persistence,
            logger = logger(),
        )
        val viewModelStore = ViewModelStore().apply { put("library-data", viewModel) }
        val mutationDriver = Thread {
            try {
                check(recoveryReadStarted.await(5, TimeUnit.SECONDS))
                Thread {
                    try {
                        runBlocking {
                            mutationAttempted.countDown()
                            persistence.withLibraryMutation {
                                persistedSnapshot.set(newSnapshot)
                                session.store.replace(listOf(newItem), emptyList(), emptyList())
                                session.publishDataState(isReady = true)
                            }
                        }
                    } catch (failure: Throwable) {
                        coordinationFailure.set(failure)
                    } finally {
                        mutationFinished.countDown()
                    }
                }.start()
                // Let the concurrent mutation reach the shared library lock before releasing the old read.
                check(mutationAttempted.await(5, TimeUnit.SECONDS))
                mutationFinished.await(250, TimeUnit.MILLISECONDS)
            } catch (failure: Throwable) {
                coordinationFailure.set(failure)
            } finally {
                allowRecoveryReadToFinish.countDown()
            }
        }
        mutationDriver.start()

        try {
            val writeResult = runBlocking { persistence.persistItems(listOf(oldItem)).await() }
            assertTrue("the setup write must fail and request recovery", writeResult.isFailure)

            pumpMainUntil("recovery and concurrent mutation to settle") {
                session.dataState.value.isReady &&
                    session.dataState.value.items.map(CodeItem::text) == listOf(newItem.text) &&
                    mutationFinished.count == 0L
            }
            mutationDriver.join(TimeUnit.SECONDS.toMillis(5))

            assertFalse("the recovery/mutation coordination must finish", mutationDriver.isAlive)
            assertNull("the recovery/mutation driver must not fail", coordinationFailure.get())
            assertEquals(
                "a write-failure reload must not publish an older snapshot over a committed mutation",
                listOf(newItem.text),
                session.dataState.value.items.map(CodeItem::text),
            )
        } finally {
            allowRecoveryReadToFinish.countDown()
            mutationDriver.join(TimeUnit.SECONDS.toMillis(5))
            viewModelStore.clear()
        }
    }

    private fun repository(
        startupSnapshot: StartupBarcodeSnapshot,
        loadFailure: Exception? = null,
        saveFailure: Exception? = null,
        startupLoadCount: AtomicInteger? = null,
    ): BarcodeRepository =
        Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "loadStartupSnapshot" -> {
                    startupLoadCount?.incrementAndGet()
                    loadFailure?.let { throw it } ?: startupSnapshot
                }
                "saveItems" -> saveFailure?.let { throw it } ?: Unit
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository

    private fun noLegacyMigration() = object : BarcodeDataMigration {
        override suspend fun migrateIfNeeded() = Unit
    }

    private fun logger() = AppLogger { _, _, _ -> throw IOException("diagnostic storage unavailable") }

    private fun pumpMainUntil(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        var dispatchFailure: Throwable? = null
        while (!condition() && System.nanoTime() < deadline && dispatchFailure == null) {
            try {
                mainLooper.idle()
            } catch (failure: Throwable) {
                dispatchFailure = failure
            }
            if (!condition() && dispatchFailure == null) Thread.sleep(5)
        }
        assertNull("$description must not crash the ViewModel coroutine", dispatchFailure)
        mainLooper.idle()
        assertTrue(description, condition())
    }
}
