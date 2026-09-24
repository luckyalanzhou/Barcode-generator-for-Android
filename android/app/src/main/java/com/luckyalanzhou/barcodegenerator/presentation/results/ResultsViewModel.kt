package com.luckyalanzhou.barcodegenerator.presentation.results

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoriteGroupContentLoadResult
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateEditorStateHolder
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageCache
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageRenderer
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Owns result-page state, image rendering, and result-to-editor preparation. */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    imageCache: BarcodeImageCache,
    private val dataSession: LibraryDataSession,
    private val persistence: BarcodePersistenceCoordinator,
    private val appLogger: AppLogger,
    private val generateEditor: GenerateEditorStateHolder,
    private val barcodeRepository: BarcodeRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val imageRenderer = BarcodeImageRenderer(imageCache)
    private val savedResult = SavedResultState(savedState)
    private val pendingRestoration = savedResult.read()
    private val results = ResultsCoordinator(pendingRestoration?.let {
        ResultUiState(returnPage = it.returnPage, isRestoring = true)
    } ?: ResultUiState())
    val resultUiState: StateFlow<ResultUiState> = results.state
    private val generation = GenerateCoordinator(dataSession.store, ::persistGeneratedItems)
    private var restorationJob: Job? = null
    private var favoriteRenderJob: Job? = null
    private var favoriteRenderRequest = 0L

    init {
        pendingRestoration?.let(::restoreSavedResult)
    }

    fun prepareMainGenerateTab() {
        restorationJob?.cancel()
        results.prepareGenerateTab()
        results.updateGeneratedResult(results.current().copy(isRestoring = false))
        savedResult.save(results.current())
    }

    fun clearSelectedFavoriteGroup() {
        results.clearSelectedFavoriteGroup()
        savedResult.save(results.current())
    }

    private fun restoreSavedResult(descriptor: SavedResultDescriptor) {
        restorationJob = viewModelScope.launch {
            try {
                val restored = withContext(Dispatchers.IO) {
                    loadSavedResult(
                        descriptor,
                        barcodeRepository::loadItemsByIds,
                        barcodeRepository::loadFavoriteGroupsByIds,
                    )
                }
                if (restored == null) {
                    results.updateGeneratedResult(ResultUiState(returnPage = descriptor.returnPage, restoreFailed = true))
                } else {
                    results.updateGeneratedResult(restored)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appLogger.record("results", "restoring saved result failed", error)
                results.updateGeneratedResult(ResultUiState(returnPage = descriptor.returnPage, restoreFailed = true))
            }
        }
    }

    private fun rememberResult(state: ResultUiState) {
        restorationJob?.cancel()
        val ready = state.copy(isRestoring = false, restoreFailed = false)
        results.updateGeneratedResult(ready)
        savedResult.save(ready)
    }

    internal fun cancelFavoriteGroupRendering() {
        favoriteRenderRequest++
        favoriteRenderJob?.cancel()
    }

    internal fun openFavoriteGroup(
        content: FavoriteGroupContentLoadResult.Loaded,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        isCurrent: (Long, Long) -> Boolean,
        onNavigateToResults: () -> Unit,
        onNotice: (String) -> Unit,
    ) {
        favoriteRenderJob?.cancel()
        val request = ++favoriteRenderRequest
        favoriteRenderJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.Default.limitedParallelism(4)) {
                    coroutineScope {
                        content.items.map { item ->
                            async { imageRenderer.loadOrCreate(item, style, dark, density) }
                        }.awaitAll()
                    }
                }
                if (request != favoriteRenderRequest || !isCurrent(content.group.id, content.expectedSavedAt)) {
                    appLogger.record("favorites", "open discarded groupId=${content.group.id} reason=stale_after_render", null)
                    return@launch
                }
                appLogger.record(
                    "favorites",
                    "open complete groupId=${content.group.id} linkIds=${content.group.itemIds.size} loadedItems=${content.items.size}",
                    null,
                )
                restorationJob?.cancel()
                results.showFavoriteResult(content.group, content.items)
                savedResult.save(results.current())
                onNavigateToResults()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appLogger.record("favorites", "open failed groupId=${content.group.id}", error)
                onNotice("读取收藏文件失败，数据未被修改；请重试")
            }
        }
    }

    internal fun prepareFavoriteGroupForEditing(
        content: FavoriteGroupContentLoadResult.Loaded,
        onLoaded: (List<CodeItem>) -> Unit,
    ) {
        restorationJob?.cancel()
        results.showFavoriteResult(content.group, content.items)
        savedResult.save(results.current())
        onLoaded(content.items)
    }

    fun showHistoryResult(batch: List<CodeItem>, onNavigateToResults: () -> Unit) {
        restorationJob?.cancel()
        results.showHistoryResult(batch)
        savedResult.save(results.current())
        onNavigateToResults()
    }

    fun editCurrentResult(onNavigateToGenerate: () -> Unit) {
        val items = results.current().items
        generateEditor.updateDraft(items.map { it.text })
        generateEditor.setPendingFormat(items.firstOrNull()?.format)
        onNavigateToGenerate()
    }

    fun commitGeneratedBarcodes(items: List<CodeItem>, onNavigateToResults: () -> Unit) {
        if (items.isEmpty()) return
        val nextResult = generation.commit(items, results.current())
        rememberResult(nextResult)
        onNavigateToResults()
    }

    fun loadOrCreateBarcodeImage(
        item: CodeItem,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
    ): Bitmap? = imageRenderer.loadOrCreate(item, style, dark, density)

    fun createBarcodeImage(
        text: String,
        format: BarcodeFormat,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean = true,
    ): Bitmap? = imageRenderer.create(text, format, style, dark, density, withBackground)

    private fun persistGeneratedItems() {
        persistence.persistItems(viewModelScope, dataSession.store.itemsSnapshot())
        dataSession.publishDataState()
    }
}
