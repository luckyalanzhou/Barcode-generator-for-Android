package com.luckyalanzhou.barcodegenerator.presentation.results

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoriteGroupContentLoadResult
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateEditorStateHolder
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageRenderer
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.recordSafely
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 负责结果页状态、条码图片准备、结果恢复，以及从结果页返回生成页时的输入快照交接。 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val imageRenderer: BarcodeImageRenderer,
    private val dataSession: LibraryDataSession,
    private val persistence: BarcodePersistenceCoordinator,
    private val appLogger: AppLogger,
    private val generateEditor: GenerateEditorStateHolder,
    private val barcodeRepository: BarcodeRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
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
    private var resultPreparationJob: Job? = null
    private var resultPreparationRequest = 0L
    private var generatedCommitInProgress = false
    private val _isPreparingResult = MutableStateFlow(false)
    val isPreparingResult: StateFlow<Boolean> = _isPreparingResult.asStateFlow()

    /** 只记录规模和渲染阶段，不写入条码正文；UI 回退也必须留下可排查原因。 */
    fun recordRowRenderFailure(error: Throwable, count: Int, width: Int) {
        appLogger.recordSafely("results", "row_prepare failed count=$count width=$width", error, error)
    }

    init {
        pendingRestoration?.let(::restoreSavedResult)
    }

    override fun onCleared() {
        resultPreparationRequest++
    }

    fun prepareMainGenerateTab() {
        cancelPendingResults()
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
                results.updateGeneratedResult(ResultUiState(returnPage = descriptor.returnPage, restoreFailed = true))
                appLogger.recordSafely("results", "restoring saved result failed", error, error)
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

    internal fun cancelPendingResults() {
        resultPreparationRequest++
        resultPreparationJob?.cancel()
        resultPreparationJob = null
        if (!generatedCommitInProgress) _isPreparingResult.value = false
        cancelFavoriteGroupRendering()
    }

    internal fun openFavoriteGroup(
        content: FavoriteGroupContentLoadResult.Loaded,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        isCurrent: (Long, Long, Long) -> Boolean,
        onNavigateToResults: () -> Unit,
        onNotice: (String) -> Unit,
    ) {
        favoriteRenderJob?.cancel()
        val request = ++favoriteRenderRequest
        favoriteRenderJob = viewModelScope.launch {
            try {
                if (!prepareBarcodeImages(content.items, style, dark, density)) {
                    onNotice("部分收藏条码生成失败，请重试")
                    return@launch
                }
                if (
                    request != favoriteRenderRequest ||
                    !isCurrent(content.group.id, content.expectedSavedAt, content.expectedMutationGeneration)
                ) {
                    appLogger.recordSafely("favorites", "open discarded groupId=${content.group.id} reason=stale_after_render")
                    return@launch
                }
                appLogger.recordSafely(
                    "favorites",
                    "open complete groupId=${content.group.id} linkIds=${content.group.itemIds.size} loadedItems=${content.items.size}",
                )
                restorationJob?.cancel()
                results.showFavoriteResult(content.group, content.items)
                savedResult.save(results.current())
                onNavigateToResults()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onNotice("读取收藏文件失败，数据未被修改；请重试")
                appLogger.recordSafely("favorites", "open failed groupId=${content.group.id}", error, error)
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

    fun showHistoryResult(
        batch: List<CodeItem>,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        onNotice: (String) -> Unit,
        onNavigateToResults: () -> Unit,
    ) {
        prepareResultImages(batch, style, dark, density, onNotice) {
            restorationJob?.cancel()
            results.showHistoryResult(batch)
            savedResult.save(results.current())
            onNavigateToResults()
        }
    }

    /** 将完整结果一次性写入生成页编辑状态，再导航回生成页，避免页面观察到半更新数据。 */
    fun editCurrentResult(onNavigateToGenerate: () -> Unit) {
        val items = results.current().items
        if (items.isEmpty()) {
            appLogger.recordSafely("results", "edit ignored because result list is empty")
            return
        }
        // 在切换页面前一次性发布完整的编辑快照。分两次写入 StateFlow 时，生成页可能在输入列表替换期间
        // 观察到不完整状态，导致首次点击回调使用旧索引并触发越界。
        generateEditor.beginEditing(items.map { it.text }, items.firstOrNull()?.format)
        onNavigateToGenerate()
    }

    /** 数据就绪且整批图片准备成功后，才持久化新条码并进入结果页；失败时保留原页面状态。 */
    fun commitGeneratedBarcodes(
        items: List<CodeItem>,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        onNotice: (String) -> Unit,
        onNavigateToResults: () -> Unit,
    ) {
        if (!dataSession.dataState.value.isReady) {
            onNotice("数据正在加载，请稍后生成")
            return
        }
        if (items.isEmpty()) return
        prepareResultImages(items, style, dark, density, onNotice) { request ->
            generatedCommitInProgress = true
            try {
                // Room 写入进入队列后可能晚于此 ViewModel 协程完成；写入一旦开始，仍需将同一提交同步到内存，切换 Tab 只取消导航。
                withContext(NonCancellable) {
                    persistence.withLibraryMutation {
                        val inserted = persistence.insertGeneratedItems(items)
                        val nextResult = generation.commit(inserted, results.current())
                        rememberResult(nextResult)
                        if (request == resultPreparationRequest) onNavigateToResults()
                    }
                }
            } finally {
                generatedCommitInProgress = false
                _isPreparingResult.value = false
            }
        }
    }

    private fun prepareResultImages(
        items: List<CodeItem>,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        onNotice: (String) -> Unit,
        onReady: suspend (request: Long) -> Unit,
    ) {
        if (items.isEmpty() || _isPreparingResult.value) return
        resultPreparationJob?.cancel()
        val request = ++resultPreparationRequest
        _isPreparingResult.value = true
        resultPreparationJob = viewModelScope.launch {
            try {
                if (!prepareBarcodeImages(items, style, dark, density)) {
                    if (request == resultPreparationRequest) onNotice("条码图片生成失败，结果尚未打开；请重试")
                    return@launch
                }
                if (request == resultPreparationRequest) onReady(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (request == resultPreparationRequest) onNotice("条码结果准备或保存失败，结果尚未打开；请重试")
                appLogger.recordSafely("results", "preparing or saving result failed", error, error)
            } finally {
                if (request == resultPreparationRequest) _isPreparingResult.value = false
            }
        }
    }

    private suspend fun prepareBarcodeImages(
        items: List<CodeItem>,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
    ): Boolean = withContext(Dispatchers.Default.limitedParallelism(8)) {
        coroutineScope {
            items.map { item ->
                async { imageRenderer.loadOrCreate(item, style, dark, density) != null }
            }.awaitAll().all { it }
        }
    }

    fun loadOrCreateBarcodeImage(
        item: CodeItem,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
    ): Bitmap? = imageRenderer.loadOrCreate(item, style, dark, density)

    fun createBarcodeImage(
        text: String,
        formatId: String,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean = true,
    ): Bitmap? = imageRenderer.create(text, formatId, style, dark, density, withBackground)

    suspend fun createBarcodePreviewImage(
        text: String,
        formatId: String,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean = true,
    ): Bitmap? {
        currentCoroutineContext().ensureActive()
        // ZXing 编码是同步操作，因此放到后台线程执行；若界面协程已取消，则回收生成的位图。
        val bitmap = withContext(NonCancellable) {
            withContext(Dispatchers.Default) {
                imageRenderer.create(text, formatId, style, dark, density, withBackground)
            }
        }
        return try {
            currentCoroutineContext().ensureActive()
            bitmap
        } catch (cancelled: CancellationException) {
            bitmap?.takeUnless { it.isRecycled }?.recycle()
            throw cancelled
        }
    }

    private fun persistGeneratedItems() {
        persistence.persistItems(dataSession.store.itemsSnapshot())
        dataSession.publishDataState()
    }
}
