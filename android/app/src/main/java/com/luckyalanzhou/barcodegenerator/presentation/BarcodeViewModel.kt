package com.luckyalanzhou.barcodegenerator.presentation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import kotlinx.coroutines.Job
import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute as AppRoute
import com.luckyalanzhou.barcodegenerator.presentation.navigation.AppRouteStateFacade
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateEditorStateHolder
import com.luckyalanzhou.barcodegenerator.presentation.favorites.*
import com.luckyalanzhou.barcodegenerator.presentation.results.ResultsCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.*
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesDataSession

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val barcodeDataCoordinator: BarcodeDataCoordinator,
    private val favoritesDataSession: FavoritesDataSession,
    private val favoritesQuerySession: FavoritesQuerySession,
    private val barcodeImageCache: BarcodeImageCache,
    private val appLogger: AppLogger,
    private val generateEditor: GenerateEditorStateHolder,
) : ViewModel() {
    private val barcodeImageRenderer = BarcodeImageRenderer(barcodeImageCache)
    private val barcodePersistence = barcodeDataCoordinator.persistence
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val routeFacade = AppRouteStateFacade(_uiState)
    val dataState: StateFlow<BarcodeDataState> = favoritesDataSession.dataState
    private val favoritesFacade = FavoritesFacade(
        persistence = barcodePersistence,
        scope = viewModelScope,
        session = favoritesDataSession,
        querySession = favoritesQuerySession,
    )
    private val favoritesStateStore = favoritesFacade.store
    private val favoritesCoordinator = favoritesFacade.mutation
    private val favoritesQueryCoordinator = favoritesFacade.query
    private val favoritesLoadCoordinator = favoritesFacade.load
    private val resultsCoordinator = ResultsCoordinator()
    val resultUiState: StateFlow<ResultUiState> = resultsCoordinator.state
    private var favoriteRenderJob: Job? = null
    private var favoriteRenderRequest = 0L

    private val generationCoordinator = GenerateCoordinator(
        store = favoritesStateStore,
        persistItems = ::persistItems,
    )

    private val _events = MutableSharedFlow<BarcodeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BarcodeEvent> = _events.asSharedFlow()
    private val _persistenceFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val persistenceFailures: SharedFlow<Unit> = _persistenceFailures.asSharedFlow()

    init {
        viewModelScope.launch {
            barcodePersistence.writeFailures.collect { error ->
                appLogger.record("persistence", "write failed", error)
                _persistenceFailures.emit(Unit)
                // Mutations are optimistic in memory. If the queued Room write fails,
                // restore the last durable snapshot instead of leaving stale UI state.
                runCatching { favoritesLoadCoordinator.loadPersistedData() }
                    .onFailure { reloadError ->
                        appLogger.record("persistence", "reload after write failure failed", reloadError)
                    }
            }
        }
    }

    /** 发布只读快照，页面不会直接观察可变集合。 */
    fun publishDataState(isReady: Boolean = dataState.value.isReady) {
        favoritesDataSession.publishDataState(isReady)
    }

    fun navigateTo(route: AppRoute, fromTabSwipe: Boolean = false) {
        // ComposeAppShell renders directly from AppUiState, so navigation is a
        // state mutation rather than a second event-driven navigation channel.
        routeFacade.navigateTo(route, fromTabSwipe)
    }

    /** Transitional UI mirror; Compose Navigation owns the destination and back stack. */
    fun syncNavigationStateFromUi(route: AppRoute, fromTabSwipe: Boolean = false) {
        routeFacade.navigateTo(route, fromTabSwipe)
    }

    fun prepareMainGenerateTab() {
        resultsCoordinator.prepareGenerateTab()
    }

    fun updateSettingsReturnPage(route: AppRoute) {
        routeFacade.updateSettingsReturnPage(route)
    }

    fun clearSelectedFavoriteGroup() {
        resultsCoordinator.clearSelectedFavoriteGroup()
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
    ) {
        favoriteRenderJob?.cancel()
        val request = ++favoriteRenderRequest
        favoriteRenderJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.Default.limitedParallelism(4)) {
                    coroutineScope {
                        content.items.map { item ->
                            async { barcodeImageRenderer.loadOrCreate(item, style, dark, density) }
                        }.awaitAll()
                    }
                }
                if (request != favoriteRenderRequest || !isCurrent(content.group.id, content.expectedSavedAt)) {
                    DebugLog.record("favorites", "open discarded groupId=${content.group.id} reason=stale_after_render")
                    return@launch
                }
                DebugLog.record(
                    "favorites",
                    "open complete groupId=${content.group.id} linkIds=${content.group.itemIds.size} loadedItems=${content.items.size}",
                )
                resultsCoordinator.showFavoriteResult(content.group, content.items)
                navigateTo(AppRoute.Results)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appLogger.record("favorites", "open failed groupId=${content.group.id}", error)
                DebugLog.record("favorites", "open failed groupId=${content.group.id}", error)
                _events.emit(BarcodeEvent.Notice("读取收藏文件失败，数据未被修改；请重试"))
            }
        }
    }

    internal fun loadFavoriteGroupForEditing(
        content: FavoriteGroupContentLoadResult.Loaded,
        onLoaded: (List<CodeItem>) -> Unit,
    ) {
        resultsCoordinator.showFavoriteResult(content.group, content.items)
        onLoaded(content.items)
        navigateTo(AppRoute.Favorites)
    }

    fun openHistoryResult(batch: List<CodeItem>) {
        resultsCoordinator.showHistoryResult(batch)
        navigateTo(AppRoute.Results)
    }

    fun editCurrentResult() {
        generateEditor.updateDraft(resultsCoordinator.current().items.map { it.text })
        generateEditor.setPendingFormat(resultsCoordinator.current().items.firstOrNull()?.format)
        navigateTo(AppRoute.Generate)
    }

    /** 结果页图片缓存的唯一入口；Compose 不直接访问文件缓存或执行条码生成。 */
    fun loadOrCreateBarcodeImage(
        item: CodeItem,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
    ): Bitmap? {
        return barcodeImageRenderer.loadOrCreate(item, style, dark, density)
    }

    fun createBarcodeImage(
        text: String,
        format: BarcodeFormat,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean = true,
    ): Bitmap? {
        return barcodeImageRenderer.create(text, format, style, dark, density, withBackground)
    }

    fun commitGeneratedBarcodes(items: List<CodeItem>) {
        if (items.isEmpty()) return
        val nextResult = generationCoordinator.commit(items, resultsCoordinator.current())
        resultsCoordinator.updateGeneratedResult(nextResult)
        navigateTo(AppRoute.Results)
    }

    fun persistAllFavorites() {
        favoritesCoordinator.persistAllFavorites()
        publishDataState()
    }

    fun persistItems() {
        barcodePersistence.persistItems(viewModelScope, favoritesStateStore.itemsSnapshot())
        publishDataState()
    }

    suspend fun loadPersistedData() {
        favoritesLoadCoordinator.loadPersistedData()
    }

    private fun refreshFavoritesAfterMutation() {
        favoritesQueryCoordinator.onMutation()
        publishDataState()
    }
}
