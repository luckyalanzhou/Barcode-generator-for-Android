package com.luckyalanzhou.barcodegenerator.presentation.results

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateEditorStateHolder
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageRenderer
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ResultsViewModelCommitCancellationTest {
    @Test
    fun tabCancellationDuringDatabaseInsertKeepsMemoryAndDatabaseInSyncWithoutNavigating() {
        val insertStarted = CountDownLatch(1)
        val releaseInsert = CountDownLatch(1)
        val persistedItems = CopyOnWriteArrayList<CodeItem>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "insertGeneratedItems" -> {
                    insertStarted.countDown()
                    check(releaseInsert.await(5, TimeUnit.SECONDS))
                    val drafts = (args[0] as List<*>).filterIsInstance<CodeItem>()
                    drafts.mapIndexed { index, draft ->
                        draft.copy(id = index + 1L, inHistory = true).also(persistedItems::add)
                    }
                }
                "saveItems" -> Unit
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply { publishDataState(isReady = true) }
        val viewModel = ResultsViewModel(
            imageRenderer = object : BarcodeImageRenderer {
                override fun loadOrCreate(item: CodeItem, style: StyleSettings, dark: Boolean, density: Float) =
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

                override fun create(
                    text: String,
                    formatId: String,
                    style: StyleSettings,
                    dark: Boolean,
                    density: Float,
                    withBackground: Boolean,
                ) = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            },
            dataSession = dataSession,
            persistence = persistence,
            appLogger = AppLogger { _, _, _ -> },
            generateEditor = GenerateEditorStateHolder(),
            barcodeRepository = repository,
            savedState = SavedStateHandle(),
        )
        var navigated = false

        viewModel.commitGeneratedBarcodes(
            items = listOf(CodeItem(0L, "commit-race", "QR_CODE", 1L)),
            style = StyleSettings(),
            dark = false,
            density = 1f,
            onNotice = {},
            onNavigateToResults = { navigated = true },
        )

        try {
            pumpMainUntil("database insert starts") { insertStarted.count == 0L }
            viewModel.cancelPendingResults()
        } finally {
            releaseInsert.countDown()
        }

        pumpMainUntil("generated item is published to the shared library") {
            dataSession.store.itemsSnapshot().any { it.text == "commit-race" }
        }
        runBlocking { persistence.awaitPendingWrites() }

        assertEquals("database insert should commit once", 1, persistedItems.size)
        assertEquals(listOf("commit-race"), dataSession.store.itemsSnapshot().map { it.text })
        assertEquals(listOf("commit-race"), viewModel.resultUiState.value.items.map { it.text })
        assertFalse("switching tabs must not be redirected to the results page", navigated)
        assertFalse("completed commit releases the in-progress state", viewModel.isPreparingResult.value)
    }

    private fun pumpMainUntil(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        while (!condition() && System.nanoTime() < deadline) {
            mainLooper.idle()
            Thread.sleep(5)
        }
        mainLooper.idle()
        assertTrue("Timed out waiting for $description", condition())
    }
}
