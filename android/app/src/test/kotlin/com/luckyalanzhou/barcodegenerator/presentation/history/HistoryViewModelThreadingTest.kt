package com.luckyalanzhou.barcodegenerator.presentation.history

import android.app.Application
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistoryViewModelThreadingTest {
    @Test
    fun refreshPublishesLargeLibrarySnapshotOffCallingThread() = runBlocking {
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ -> error("Unexpected repository operation: ${method.name}") } as BarcodeRepository
        val dataSession = LibraryDataSession()
        val originalItems = List(800) { index -> CodeItem(index.toLong(), "old-$index", "Code 128-B") }
        dataSession.store.replace(originalItems, emptyList(), emptyList())
        dataSession.publishDataState(isReady = true)
        dataSession.store.replace(
            originalItems + CodeItem(801L, "new", "Code 128-B"),
            emptyList(),
            emptyList(),
        )
        val viewModel = HistoryViewModel(
            dataSession,
            BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
                override suspend fun migrateIfNeeded() = Unit
            }),
        )
        val callerThread = Thread.currentThread()
        val publishThread = AtomicReference<Thread?>()
        val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val observer = observerScope.launch {
            dataSession.dataState.collect { state ->
                if (state.items.lastOrNull()?.text == "new") publishThread.set(Thread.currentThread())
            }
        }

        viewModel.refreshHistory()
        withTimeout(5_000L) {
            while (viewModel.refreshGeneration.value == 0L) delay(10L)
        }

        assertTrue("refreshed library snapshot must be observed", publishThread.get() != null)
        assertNotSame("large library snapshot must not be copied on the caller thread", callerThread, publishThread.get())
        observer.cancel()
        observerScope.cancel()
    }
}
