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
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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
    fun generatedCommitWaitsForLibraryMutationAndPersistsImportedSnapshot() = runBlocking {
        val oldItem = CodeItem(1L, "old-item", "Code 128-B", favorite = false)
        val importedItem = CodeItem(1L, "imported-item", "Code 128-B", favorite = true, folder = "导入目录")
        val group = com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup(
            2L,
            "导入目录",
            "导入文件",
            1L,
            mutableListOf(importedItem.id),
        )
        val imagePrepared = CompletableDeferred<Unit>()
        val insertStarted = CompletableDeferred<Unit>()
        val persistedItems = CompletableDeferred<List<CodeItem>>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "insertGeneratedItems" -> {
                    insertStarted.complete(Unit)
                    @Suppress("UNCHECKED_CAST")
                    (args!![0] as List<CodeItem>).mapIndexed { index, item ->
                        item.copy(id = index + 10L, inHistory = true)
                    }
                }
                "saveItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    persistedItems.complete((args!![0] as List<CodeItem>).map(CodeItem::copy))
                    Unit
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as BarcodeRepository
        val persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
            override suspend fun migrateIfNeeded() = Unit
        })
        val dataSession = LibraryDataSession().apply {
            store.replace(listOf(oldItem), emptyList(), emptyList())
            publishDataState(isReady = true)
        }
        val viewModel = ResultsViewModel(
            imageRenderer = object : BarcodeImageRenderer {
                override fun loadOrCreate(item: CodeItem, style: StyleSettings, dark: Boolean, density: Float) =
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also { imagePrepared.complete(Unit) }

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

        persistence.withLibraryMutation {
            viewModel.commitGeneratedBarcodes(
                items = listOf(CodeItem(0L, "generated", "Code 128-B", 2L)),
                style = StyleSettings(),
                dark = false,
                density = 1f,
                onNotice = { error("Unexpected result preparation failure: $it") },
                onNavigateToResults = {},
            )
            pumpMainUntil("generated result image preparation") { imagePrepared.isCompleted }
            pumpMainFor(250)

            assertFalse(
                "generated insertion must wait until the import snapshot is refreshed",
                insertStarted.isCompleted,
            )
            dataSession.store.replace(listOf(importedItem), listOf(group), listOf("导入目录"))
            dataSession.publishDataState(isReady = true)
        }

        pumpMainUntil("generated snapshot is persisted") { persistedItems.isCompleted }
        val saved = persistedItems.await()
        val imported = saved.single { it.id == importedItem.id }
        assertEquals("imported-item", imported.text)
        assertTrue("the imported favorite flag must survive generated-item commit", imported.favorite)
        assertEquals("导入目录", imported.folder)
        assertTrue(saved.any { it.text == "generated" })
        persistence.awaitPendingWrites()
    }

    @Test
    fun previewImageGenerationDoesNotRunOnTheCallerThread() = runBlocking {
        val callerThread = Thread.currentThread()
        val rendererThread = AtomicReference<Thread?>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ -> error("Unexpected repository operation: ${method.name}") } as BarcodeRepository
        val viewModel = ResultsViewModel(
            imageRenderer = object : BarcodeImageRenderer {
                override fun loadOrCreate(item: CodeItem, style: StyleSettings, dark: Boolean, density: Float) = null

                override fun create(
                    text: String,
                    formatId: String,
                    style: StyleSettings,
                    dark: Boolean,
                    density: Float,
                    withBackground: Boolean,
                ) = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also {
                    rendererThread.set(Thread.currentThread())
                }
            },
            dataSession = LibraryDataSession(),
            persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
                override suspend fun migrateIfNeeded() = Unit
            }),
            appLogger = AppLogger { _, _, _ -> },
            generateEditor = GenerateEditorStateHolder(),
            barcodeRepository = repository,
            savedState = SavedStateHandle(),
        )

        val bitmap = requireNotNull(
            viewModel.createBarcodePreviewImage("preview", "QR_CODE", StyleSettings(), false, 1f),
        )
        try {
            assertTrue("barcode encoding must not block its caller", rendererThread.get() !== callerThread)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun cancellingPreviewAfterRenderingRecyclesTheUnclaimedBitmap() = runBlocking {
        val renderStarted = CountDownLatch(1)
        val finishRender = CountDownLatch(1)
        val renderedBitmap = AtomicReference<Bitmap?>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ -> error("Unexpected repository operation: ${method.name}") } as BarcodeRepository
        val viewModel = ResultsViewModel(
            imageRenderer = object : BarcodeImageRenderer {
                override fun loadOrCreate(item: CodeItem, style: StyleSettings, dark: Boolean, density: Float) = null

                override fun create(
                    text: String,
                    formatId: String,
                    style: StyleSettings,
                    dark: Boolean,
                    density: Float,
                    withBackground: Boolean,
                ): Bitmap {
                    renderStarted.countDown()
                    check(finishRender.await(5, TimeUnit.SECONDS))
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also(renderedBitmap::set)
                }
            },
            dataSession = LibraryDataSession(),
            persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
                override suspend fun migrateIfNeeded() = Unit
            }),
            appLogger = AppLogger { _, _, _ -> },
            generateEditor = GenerateEditorStateHolder(),
            barcodeRepository = repository,
            savedState = SavedStateHandle(),
        )
        val preview = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.createBarcodePreviewImage("preview", "QR_CODE", StyleSettings(), false, 1f)
        }

        try {
            assertTrue("background rendering should start", renderStarted.await(5, TimeUnit.SECONDS))
            preview.cancel()
        } finally {
            finishRender.countDown()
        }
        preview.join()

        assertTrue("a bitmap rejected by lifecycle cancellation must be recycled", requireNotNull(renderedBitmap.get()).isRecycled)
    }

    @Test
    fun editingAResultPublishesTheCompleteDraftBeforeNavigation() {
        val editor = GenerateEditorStateHolder()
        val renderedBitmaps = CopyOnWriteArrayList<Bitmap>()
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ -> error("Unexpected repository operation: ${method.name}") } as BarcodeRepository
        val viewModel = ResultsViewModel(
            imageRenderer = object : BarcodeImageRenderer {
                override fun loadOrCreate(item: CodeItem, style: StyleSettings, dark: Boolean, density: Float) =
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also(renderedBitmaps::add)

                override fun create(
                    text: String,
                    formatId: String,
                    style: StyleSettings,
                    dark: Boolean,
                    density: Float,
                    withBackground: Boolean,
                ) = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            },
            dataSession = LibraryDataSession(),
            persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
                override suspend fun migrateIfNeeded() = Unit
            }),
            appLogger = AppLogger { _, _, _ -> },
            generateEditor = editor,
            barcodeRepository = repository,
            savedState = SavedStateHandle(),
        )
        val items = listOf(
            CodeItem(2, "SECOND", "QR Code", 2L),
            CodeItem(1, "FIRST", "Code 128-B", 1L),
        )
        var navigationSnapshot: com.luckyalanzhou.barcodegenerator.presentation.GenerateEditorState? = null

        try {
            viewModel.showHistoryResult(
                batch = items,
                style = StyleSettings(),
                dark = false,
                density = 1f,
                onNotice = { error("Unexpected result preparation failure: $it") },
                onNavigateToResults = {},
            )
            pumpMainUntil("result snapshot is ready") { viewModel.resultUiState.value.items.size == items.size }

            viewModel.editCurrentResult { navigationSnapshot = editor.state.value }

            val handedOff = requireNotNull(navigationSnapshot)
            assertEquals(listOf("FIRST", "SECOND"), handedOff.inputDraft)
            assertEquals("Code 128-B", handedOff.pendingFormat)
            assertEquals("Code 128-B", handedOff.formatName)
        } finally {
            renderedBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

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

    private fun pumpMainFor(durationMs: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs)
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        while (System.nanoTime() < deadline) {
            mainLooper.idle()
            Thread.sleep(5)
        }
        mainLooper.idle()
    }
}
