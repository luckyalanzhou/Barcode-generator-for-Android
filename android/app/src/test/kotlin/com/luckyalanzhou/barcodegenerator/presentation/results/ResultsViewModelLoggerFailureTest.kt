package com.luckyalanzhou.barcodegenerator.presentation.results

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoriteGroupContentLoadResult
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateEditorStateHolder
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageRenderer
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
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
class ResultsViewModelLoggerFailureTest {
    @Test
    fun preparationFailureNoticeIsDeliveredWhenDiagnosticFails() {
        val item = CodeItem(23L, "history-item", "Code 128-B")
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ -> error("Unexpected repository operation: ${method.name}") } as BarcodeRepository
        val viewModel = ResultsViewModel(
            imageRenderer = object : BarcodeImageRenderer {
                override fun loadOrCreate(item: CodeItem, style: StyleSettings, dark: Boolean, density: Float): Bitmap? =
                    throw IOException("cache and barcode rendering failed")

                override fun create(
                    text: String,
                    formatId: String,
                    style: StyleSettings,
                    dark: Boolean,
                    density: Float,
                    withBackground: Boolean,
                ) = null
            },
            dataSession = LibraryDataSession(),
            persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
                override suspend fun migrateIfNeeded() = Unit
            }),
            appLogger = AppLogger { _, _, _ -> error("diagnostic storage unavailable") },
            generateEditor = GenerateEditorStateHolder(),
            barcodeRepository = repository,
            savedState = SavedStateHandle(),
        )
        var notice: String? = null
        var navigated = false

        viewModel.showHistoryResult(
            batch = listOf(item),
            style = StyleSettings(),
            dark = false,
            density = 1f,
            onNotice = { notice = it },
            onNavigateToResults = { navigated = true },
        )

        pumpMainUntil("result preparation finishes") { !viewModel.isPreparingResult.value }

        assertTrue("render failure must still reach the user", notice?.contains("失败") == true)
        assertTrue("a failed result must not navigate", !navigated)
    }

    @Test
    fun restorationFailureLeavesLoadingStateWhenDiagnosticFails() {
        val item = CodeItem(17L, "saved-item", "Code 128-B")
        val savedState = SavedStateHandle()
        SavedResultState(savedState).save(com.luckyalanzhou.barcodegenerator.presentation.ResultUiState(items = listOf(item)))
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ ->
            if (method.name == "loadItemsByIds") throw IllegalStateException("saved result unavailable")
            error("Unexpected repository operation: ${method.name}")
        } as BarcodeRepository
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
                ) = null
            },
            dataSession = LibraryDataSession(),
            persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
                override suspend fun migrateIfNeeded() = Unit
            }),
            appLogger = AppLogger { _, _, _ -> error("diagnostic storage unavailable") },
            generateEditor = GenerateEditorStateHolder(),
            barcodeRepository = repository,
            savedState = savedState,
        )

        pumpMainUntil("failed result restoration state") { viewModel.resultUiState.value.restoreFailed }

        assertTrue("failed restoration must stop the loading indicator", !viewModel.resultUiState.value.isRestoring)
        assertFalse("failed restoration must not expose a partial result", viewModel.resultUiState.value.items.isNotEmpty())
    }

    @Test
    fun favoriteGroupStillOpensWhenCompletionDiagnosticFails() {
        val item = CodeItem(11L, "favorite-item", "Code 128-B")
        val group = FavoriteGroup(9L, "根目录", "收藏文件", 1L, mutableListOf(item.id))
        val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val repository = Proxy.newProxyInstance(
            BarcodeRepository::class.java.classLoader,
            arrayOf(BarcodeRepository::class.java),
        ) { _, method, _ -> error("Unexpected repository operation: ${method.name}") } as BarcodeRepository
        val viewModel = ResultsViewModel(
            imageRenderer = object : BarcodeImageRenderer {
                override fun loadOrCreate(item: CodeItem, style: StyleSettings, dark: Boolean, density: Float) = image

                override fun create(
                    text: String,
                    formatId: String,
                    style: StyleSettings,
                    dark: Boolean,
                    density: Float,
                    withBackground: Boolean,
                ) = image
            },
            dataSession = LibraryDataSession(),
            persistence = BarcodePersistenceCoordinator(repository, object : BarcodeDataMigration {
                override suspend fun migrateIfNeeded() = Unit
            }),
            appLogger = AppLogger { _, message, _ ->
                if (message.startsWith("open complete")) error("diagnostic storage unavailable")
            },
            generateEditor = GenerateEditorStateHolder(),
            barcodeRepository = repository,
            savedState = SavedStateHandle(),
        )
        var navigated = false
        var notice: String? = null

        try {
            viewModel.openFavoriteGroup(
                content = FavoriteGroupContentLoadResult.Loaded(
                    group = group,
                    items = listOf(item),
                    expectedSavedAt = group.savedAt,
                    expectedMutationGeneration = 1L,
                ),
                style = StyleSettings(),
                dark = false,
                density = 1f,
                isCurrent = { _, _, _ -> true },
                onNavigateToResults = { navigated = true },
                onNotice = { notice = it },
            )

            pumpMainUntil("favorite group open callback") { navigated || notice != null }

            assertTrue("a failed success diagnostic must not block navigation", navigated)
            assertNull("a successful open must not show a failure notice", notice)
            assertEquals(listOf(item), viewModel.resultUiState.value.items)
            assertEquals(group.id, viewModel.resultUiState.value.selectedFavoriteGroup?.id)
        } finally {
            image.recycle()
        }
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
