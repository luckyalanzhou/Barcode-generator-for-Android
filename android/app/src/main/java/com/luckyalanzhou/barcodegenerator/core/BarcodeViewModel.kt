package com.luckyalanzhou.barcodegenerator

import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import com.luckyalanzhou.barcodegenerator.ui.DebugLog
import kotlinx.coroutines.Job
import com.luckyalanzhou.barcodegenerator.NavigationRoute as AppRoute
import com.luckyalanzhou.barcodegenerator.data.LocalBarcodeFileStore
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val barcodeDataCoordinator: BarcodeDataCoordinator,
    private val generateBarcodesUseCase: GenerateBarcodesUseCase,
    private val localBarcodeFileStore: LocalBarcodeFileStore,
    private val updateDownloadService: UpdateDownloadService,
    private val updateCheckService: UpdateCheckService,
    private val ocrTextService: OcrTextService,
    private val barcodeDecodeService: BarcodeDecodeService,
    private val apkUpdateValidator: ApkUpdateValidator,
    private val appLogger: AppLogger,
    private val generateEditor: GenerateEditorStateHolder,
) : ViewModel() {
    private val barcodeImageRenderer = BarcodeImageRenderer(localBarcodeFileStore)
    private val barcodePersistence = barcodeDataCoordinator.persistence
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val navigationStateCoordinator = NavigationStateCoordinator(_uiState)
    private val _navigationEvents = MutableSharedFlow<NavigationRequest>(extraBufferCapacity = 8)
    val navigationEvents: SharedFlow<NavigationRequest> = _navigationEvents.asSharedFlow()

    /** 条码与收藏的内部工作集合；对外只发布不可变状态快照。 */
    private val favoritesStateStore = FavoritesStateStore()
    /** 搜索结果与普通分页列表隔离，避免多次搜索持续膨胀普通收藏状态。 */
    private val favoriteSearchStateStore = FavoritesStateStore()
    private val favoritesCoordinator = FavoritesMutationCoordinator(
        store = favoritesStateStore,
        persistence = barcodePersistence,
        scope = viewModelScope,
    )
    private val favoritesQueryCoordinator = FavoritesQueryCoordinator(
        repository = barcodeDataCoordinator.repository,
        store = favoritesStateStore,
        searchStore = favoriteSearchStateStore,
    )
    private val historyCoordinator = HistoryCoordinator(
        store = favoritesStateStore,
        persistItems = { items -> barcodePersistence.persistItems(viewModelScope, items) },
    )

    private val _dataState = MutableStateFlow(BarcodeDataState())
    val dataState: StateFlow<BarcodeDataState> = _dataState.asStateFlow()
    private val _favoriteSearchState = MutableStateFlow(BarcodeDataState())
    val favoriteSearchState: StateFlow<BarcodeDataState> = _favoriteSearchState.asStateFlow()
    private val favoritesPageStateCoordinator = FavoritesPageStateCoordinator()
    val favoritePageQuery: StateFlow<String> = favoritesPageStateCoordinator.query
    private val dataStateCoordinator = BarcodeDataStateCoordinator(
        store = favoritesStateStore,
        state = _dataState,
    )
    private val favoritesLoadCoordinator = FavoritesLoadCoordinator(
        persistence = barcodePersistence,
        store = favoritesStateStore,
        query = favoritesQueryCoordinator,
        publish = { isReady -> dataStateCoordinator.publish(isReady) },
        publishSearch = { publishFavoriteSearchState() },
    )

    private val resultsCoordinator = ResultsCoordinator()
    val resultUiState: StateFlow<ResultUiState> = resultsCoordinator.state
    private var favoriteOpenJob: Job? = null
    private var favoriteOpenRequest = 0L

    private val generationCoordinator = GenerateCoordinator(
        useCase = generateBarcodesUseCase,
        store = favoritesStateStore,
        readDraft = { generateEditor.state.value.inputDraft },
        persistItems = ::persistItems,
    )

    val favoriteTreeUiState: StateFlow<FavoriteTreeUiState> = favoritesPageStateCoordinator.treeState

    private val cameraRequestCoordinator = CameraRequestCoordinator()
    val cameraCaptureState: StateFlow<CameraCaptureState> = cameraRequestCoordinator.state
    private var favoriteSearchJob: Job? = null
    private var currentFavoriteSearchQuery = ""

    private val updateCoordinator = UpdateCoordinator(updateDownloadService, updateCheckService, apkUpdateValidator, appLogger)
    val updateUiState: StateFlow<UpdateUiState> = updateCoordinator.uiState
    val updateDownloadUiState: StateFlow<UpdateDownloadUiState> = updateCoordinator.downloadUiState
    val updateEvents: SharedFlow<UpdateEvent> = updateCoordinator.events

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
    fun publishDataState(isReady: Boolean = _dataState.value.isReady) {
        dataStateCoordinator.publish(isReady)
    }

    fun navigateTo(route: AppRoute, fromTabSwipe: Boolean = false) {
        _navigationEvents.tryEmit(NavigationRequest(route, fromTabSwipe))
    }

    /** Transitional UI mirror; Compose Navigation owns the destination and back stack. */
    fun syncNavigationStateFromUi(route: AppRoute, fromTabSwipe: Boolean = false) {
        navigationStateCoordinator.navigateTo(route, fromTabSwipe)
    }

    fun prepareMainGenerateTab() {
        resultsCoordinator.prepareGenerateTab()
    }

    fun updateSettingsReturnPage(route: AppRoute) {
        navigationStateCoordinator.updateSettingsReturnPage(route)
    }

    fun clearSelectedFavoriteGroup() {
        resultsCoordinator.clearSelectedFavoriteGroup()
    }

    fun openFavoriteGroup(
        group: FavoriteGroup,
        style: StyleSettings? = null,
        dark: Boolean = false,
        density: Float = 1f,
    ) {
        openFavoriteGroupWhenLoaded(group, AppRoute.Results, style, dark, density)
    }

    fun loadFavoriteGroupForEditing(group: FavoriteGroup, onLoaded: (List<CodeItem>) -> Unit) {
        openFavoriteGroupWhenLoaded(group, AppRoute.Favorites, null, false, 1f, onLoaded)
    }

    private fun openFavoriteGroupWhenLoaded(
        group: FavoriteGroup,
        destination: AppRoute,
        style: StyleSettings?,
        dark: Boolean,
        density: Float,
        onLoaded: ((List<CodeItem>) -> Unit)? = null,
    ) {
        favoriteOpenJob?.cancel()
        val request = ++favoriteOpenRequest
        favoriteOpenJob = viewModelScope.launch {
            try {
                val cachedGroup = findCachedFavoriteGroup(group.id)
                if (cachedGroup == null) {
                    DebugLog.record("favorites", "open skipped groupId=${group.id} reason=group_not_loaded")
                    _events.emit(BarcodeEvent.Notice("收藏文件已变化，请刷新收藏列表后重试"))
                    return@launch
                }
                val expectedSavedAt = cachedGroup.savedAt
                DebugLog.record("favorites", "open start groupId=${group.id} destination=$destination")

                // The group row, its links, and all linked barcode rows are read from Room
                // in one transaction; the in-memory startup/page cache is never the source
                // of result contents.
                val content = withContext(Dispatchers.IO) {
                    barcodeDataCoordinator.repository.loadFavoriteGroupContent(group.id)
                }
                if (request != favoriteOpenRequest) return@launch
                if (content == null) {
                    DebugLog.record("favorites", "open failed groupId=${group.id} reason=group_missing_in_room")
                    _events.emit(BarcodeEvent.Notice("收藏文件已不存在，请刷新收藏列表"))
                    return@launch
                }
                val latestCachedGroup = findCachedFavoriteGroup(group.id)
                if (latestCachedGroup == null || latestCachedGroup.savedAt != expectedSavedAt) {
                    DebugLog.record("favorites", "open discarded groupId=${group.id} reason=group_changed_during_read")
                    return@launch
                }
                if (content.invalidItemIds.isNotEmpty()) {
                    DebugLog.record(
                        "favorites",
                        "open integrity failure groupId=${group.id} linked=${content.group.itemIds.size} loaded=${content.items.size} invalid=${content.invalidItemIds.size}",
                    )
                    _events.emit(BarcodeEvent.Notice("收藏文件数据不完整，已阻止打开空结果；请先导出备份并联系支持"))
                    return@launch
                }
                if (content.items.isEmpty()) {
                    DebugLog.record("favorites", "open empty groupId=${group.id} linked=0")
                    _events.emit(BarcodeEvent.Notice("该收藏文件没有条码内容，未进入结果页"))
                    return@launch
                }

                cacheFavoriteGroupContent(content.group, content.items)
                if (destination == AppRoute.Results && style != null) {
                    withContext(Dispatchers.Default.limitedParallelism(4)) {
                        coroutineScope {
                            content.items.map { item ->
                                async { barcodeImageRenderer.loadOrCreate(item, style, dark, density) }
                            }.awaitAll()
                        }
                    }
                }
                if (request != favoriteOpenRequest || findCachedFavoriteGroup(group.id)?.savedAt != expectedSavedAt) {
                    DebugLog.record("favorites", "open discarded groupId=${group.id} reason=stale_after_render")
                    return@launch
                }
                DebugLog.record(
                    "favorites",
                    "open complete groupId=${group.id} linkIds=${content.group.itemIds.size} loadedItems=${content.items.size}",
                )
                resultsCoordinator.showFavoriteResult(content.group, content.items)
                onLoaded?.invoke(content.items)
                if (destination == AppRoute.Generate) {
                    generateEditor.updateDraft(content.items.map { it.text })
                    generateEditor.setPendingFormat(content.items.first().format)
                }
                navigateTo(destination)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appLogger.record("favorites", "open failed groupId=${group.id}", error)
                DebugLog.record("favorites", "open failed groupId=${group.id}", error)
                _events.emit(BarcodeEvent.Notice("读取收藏文件失败，数据未被修改；请重试"))
            } finally {
            }
        }
    }

    private fun findCachedFavoriteGroup(groupId: Long): FavoriteGroup? =
        favoritesStateStore.groupsSnapshot().firstOrNull { it.id == groupId }
            ?: favoriteSearchStateStore.groupsSnapshot().firstOrNull { it.id == groupId }

    private fun cacheFavoriteGroupContent(group: FavoriteGroup, items: List<CodeItem>) {
        fun FavoritesStateStore.updateIfPresent() {
            edit {
                val groupIndex = groups.indexOfFirst { it.id == group.id }
                if (groupIndex >= 0) groups[groupIndex] = group.copy(itemIds = group.itemIds.toMutableList())
                val loadedIds = items.mapTo(HashSet()) { it.id }
                this.items.removeAll { it.id in loadedIds }
                this.items.addAll(items.map(CodeItem::copy))
            }
            markGroupLinksLoaded(group.id)
        }
        favoritesStateStore.updateIfPresent()
        favoriteSearchStateStore.updateIfPresent()
        publishDataState()
        publishFavoriteSearchState()
    }

    fun searchFavoriteContent(query: String) {
        favoriteSearchJob?.cancel()
        currentFavoriteSearchQuery = query.trim()
        favoriteSearchJob = viewModelScope.launch {
            favoritesQueryCoordinator.search(currentFavoriteSearchQuery)
            publishFavoriteSearchState()
        }
    }

    private fun publishFavoriteSearchState() {
        _favoriteSearchState.value = favoritesQueryCoordinator.searchSnapshot(_dataState.value.isReady).copy(
            folders = _dataState.value.folders,
        )
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

    fun deleteHistoryBatch(batch: List<CodeItem>) {
        historyCoordinator.deleteBatch(batch)
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

    fun createFavoriteFolder(path: String): Boolean {
        val added = favoritesStateStore.edit {
            if (path.isBlank() || path in folders) return@edit false
            folders.add(path)
            true
        }
        if (!added) return false
        persistFavoriteFolders()
        return true
    }

    fun prepareCameraRequest(requestCode: Int) {
        cameraRequestCoordinator.prepare(requestCode)
    }

    fun setCameraOutput(uri: Uri?, file: File?) {
        cameraRequestCoordinator.setOutput(uri, file)
    }

    fun markCameraCaptureStarted(nowMillis: Long = System.currentTimeMillis()) {
        cameraRequestCoordinator.markStarted(nowMillis)
    }

    fun clearCameraOutput(): CameraCaptureState {
        return cameraRequestCoordinator.clearOutput()
    }

    fun beginExternalActivityRequest(requestCode: Int) {
        cameraRequestCoordinator.beginExternalActivity(requestCode)
    }

    fun consumeExternalActivityRequest(): Int {
        return cameraRequestCoordinator.consumeExternalActivity()
    }

    fun beginPermissionRequest(requestCode: Int) {
        cameraRequestCoordinator.beginPermission(requestCode)
    }

    fun consumePermissionRequest(): Int {
        return cameraRequestCoordinator.consumePermission()
    }

    fun generateBarcodes(formatName: String): GenerateBarcodesUseCase.Output {
        val result = generationCoordinator.generate(formatName, resultsCoordinator.current())
        result.uiState?.let {
            resultsCoordinator.updateGeneratedResult(it)
            navigateTo(AppRoute.Results)
        }
        return result.output
    }

    fun recognizeText(bitmap: Bitmap, confusionMask: Int) {
        viewModelScope.launch {
            val normalized = ocrTextService.recognize(bitmap, confusionMask)
            if (normalized.isEmpty()) _events.emit(BarcodeEvent.Notice("未识别到文字，请拍摄清晰、正面的屏幕区域"))
            else {
                _events.emit(BarcodeEvent.RecognizedText(normalized))
                _events.emit(BarcodeEvent.Notice("文字识别成功，已按行添加到输入框"))
            }
        }
    }

    suspend fun decodeBarcode(bitmap: Bitmap): String? = barcodeDecodeService.decode(bitmap)
    fun setStartupUpdateCheckStarted(value: Boolean) = updateCoordinator.setStartupCheckStarted(value)
    suspend fun checkForUpdates(): UpdateCheckResult = updateCoordinator.checkForUpdates()
    fun setAvailableUpdate(version: String?, url: String?, expectedSize: Long?, sha256: String?) =
        updateCoordinator.setAvailableUpdate(version, url, expectedSize, sha256)
    fun clearAvailableUpdate() = updateCoordinator.clearAvailableUpdate()
    fun setUpdateDialogShowing(value: Boolean) = updateCoordinator.setDialogShowing(value)
    fun setUpdateDownloadRunning(value: Boolean) = updateCoordinator.setDownloadRunning(value)
    fun resetUpdateDownloadState() = updateCoordinator.resetDownloadState()
    fun setUpdateDownloadProgress(progress: Int, indeterminate: Boolean, status: String) =
        updateCoordinator.setDownloadProgress(progress, indeterminate, status)
    suspend fun downloadUpdate(apkUrl: String, expectedSize: Long?, expectedSha256: String?): File =
        updateCoordinator.downloadUpdate(apkUrl, expectedSize, expectedSha256)
    fun validateDownloadedApk(file: File) = updateCoordinator.validateDownloadedApk(file)
    fun startUpdateDownload(apkUrl: String, expectedSize: Long?, expectedSha256: String?) =
        updateCoordinator.startDownload(viewModelScope, apkUrl, expectedSize, expectedSha256)
    fun cancelUpdateDownload() = updateCoordinator.cancelDownload()
    fun setPendingInstallPath(path: String?) = updateCoordinator.setPendingInstallPath(path)

    fun takePendingInstallPath(): String? = updateCoordinator.takePendingInstallPath()
    fun syncFavoriteTree(folders: Set<String>) {
        favoritesPageStateCoordinator.syncTree(folders)
    }

    fun updateFavoritePageQuery(query: String) {
        favoritesPageStateCoordinator.updateQuery(query)
    }

    fun favoriteListPosition(): Pair<Int, Int> = favoritesPageStateCoordinator.position()

    fun rememberFavoriteListPosition(index: Int, offset: Int) {
        favoritesPageStateCoordinator.rememberPosition(index, offset)
    }

    fun addCollapsedFavoriteFolders(paths: Set<String>) {
        favoritesPageStateCoordinator.addCollapsed(paths)
    }

    fun toggleFavoriteFolder(path: String, folders: Set<String>) {
        favoritesPageStateCoordinator.toggleFolder(path, folders)
    }

    fun updateFavoriteSearch(expandedPaths: Set<String>, searching: Boolean) {
        favoritesPageStateCoordinator.updateSearch(expandedPaths, searching)
    }

    fun renameFavoriteFolder(path: String, renamedPath: String) {
        favoritesCoordinator.renameFolder(path, renamedPath)
        refreshFavoritesAfterMutation()
    }

    fun deleteFavoriteFolder(path: String) { favoritesCoordinator.deleteFolderAndPersist(path); refreshFavoritesAfterMutation() }

    fun deleteFavoriteGroup(groupId: Long) { favoritesCoordinator.deleteGroupAndPersist(groupId); refreshFavoritesAfterMutation() }

    fun deleteBarcodeItem(itemId: Long) { favoritesCoordinator.deleteItem(itemId); refreshFavoritesAfterMutation() }

    fun updateBarcodeItem(itemId: Long, text: String, format: String) { favoritesCoordinator.updateItem(itemId, text, format); refreshFavoritesAfterMutation() }

    fun renameFavoriteFolderAndPersist(path: String, renamedPath: String) {
        favoritesCoordinator.renameFolderAndPersist(path, renamedPath)
        refreshFavoritesAfterMutation()
    }

    fun deleteFavoriteFolderAndPersist(path: String) {
        favoritesCoordinator.deleteFolderAndPersist(path)
        refreshFavoritesAfterMutation()
    }

    fun renameFavoriteGroupAndPersist(groupId: Long, name: String) {
        favoritesCoordinator.renameGroupAndPersist(groupId, name)
        refreshFavoritesAfterMutation()
    }

    fun moveFavoriteGroupAndPersist(groupId: Long, folder: String) {
        favoritesCoordinator.moveGroupAndPersist(groupId, folder)
        refreshFavoritesAfterMutation()
    }

    fun deleteFavoriteGroupAndPersist(groupId: Long) {
        favoritesCoordinator.deleteGroupAndPersist(groupId)
        refreshFavoritesAfterMutation()
    }

    fun clearFavoritesAndPersist() {
        favoritesCoordinator.clearFavoritesAndPersist()
        refreshFavoritesAfterMutation()
    }

    fun clearHistoryAndPersist() {
        historyCoordinator.clearHistory()
        refreshFavoritesAfterMutation()
    }

    fun saveResultAsFavorite(
        resultItemIds: List<Long>,
        editingGroupId: Long?,
        targetGroupId: Long?,
        folder: String,
        name: String,
    ): Boolean {
        if (!favoritesCoordinator.saveResultAsFavorite(resultItemIds, editingGroupId, targetGroupId, folder, name)) return false
        refreshFavoritesAfterMutation()
        resultsCoordinator.clearSelectedFavoriteGroup()
        navigateTo(AppRoute.Favorites)
        return true
    }

    fun updateFavoriteGroupAndPersist(groupId: Long, name: String, folder: String): Boolean {
        if (favoritesStateStore.groupsSnapshot().none { it.id == groupId }) return false
        if (!favoritesCoordinator.updateGroup(groupId, name, folder)) return false
        refreshFavoritesAfterMutation()
        resultsCoordinator.updateSelectedFavoriteGroup(
            favoritesStateStore.groupsSnapshot().firstOrNull { group -> group.id == groupId },
        )
        return true
    }

    fun persistAllFavorites() {
        favoritesCoordinator.persistAllFavorites()
        publishDataState()
    }

    fun persistItems() {
        favoritesCoordinator.persistItems()
        publishDataState()
    }

    suspend fun loadPersistedData() {
        favoritesLoadCoordinator.loadPersistedData()
        if (currentFavoriteSearchQuery.isNotBlank()) {
            searchFavoriteContent(currentFavoriteSearchQuery)
        }
    }

    fun loadMoreFavoriteGroups(search: String = "") {
        if (search.isNotBlank()) {
            viewModelScope.launch { favoritesLoadCoordinator.loadMoreFavoriteGroups(search) }
            return
        }
        viewModelScope.launch { favoritesLoadCoordinator.loadMoreFavoriteGroups() }
    }

    suspend fun inspectFavoriteImport(backup: InterchangeBackup): FavoritesImportConflictSummary =
        barcodeDataCoordinator.inspectFavoriteImport(backup)

    suspend fun importFavorites(backup: InterchangeBackup, overwriteConflicts: Boolean = false): Pair<Int, Int> {
        val counts = barcodeDataCoordinator.importFavorites(backup, overwriteConflicts)
        // Reuse the startup snapshot path so import does not load every group
        // into memory or disable cursor paging after the refresh.
        loadPersistedData()
        return counts
    }

    suspend fun exportFavorites(): ByteArray = barcodeDataCoordinator.exportFavorites()

    fun restoreFavorites(bytes: ByteArray): InterchangeBackup = barcodeDataCoordinator.restoreFavorites(bytes)

    fun persistFavoriteFolders() {
        favoritesCoordinator.persistFolders()
        publishDataState()
    }

    private fun refreshFavoritesAfterMutation() {
        favoritesQueryCoordinator.onMutation()
        publishDataState()
        if (currentFavoriteSearchQuery.isNotBlank()) {
            searchFavoriteContent(currentFavoriteSearchQuery)
        } else {
            favoritesQueryCoordinator.clearSearch()
            publishFavoriteSearchState()
        }
    }
}
