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
) : ViewModel() {
    private val barcodeImageRenderer = BarcodeImageRenderer(localBarcodeFileStore)
    private val barcodePersistence = barcodeDataCoordinator.persistence
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val navigationStateCoordinator = NavigationStateCoordinator(_uiState)

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

    private val _generateEditorState = MutableStateFlow(GenerateEditorState())
    val generateEditorState: StateFlow<GenerateEditorState> = _generateEditorState.asStateFlow()

    private val _resultUiState = MutableStateFlow(ResultUiState())
    val resultUiState: StateFlow<ResultUiState> = _resultUiState.asStateFlow()

    private val generationCoordinator = GenerateCoordinator(
        useCase = generateBarcodesUseCase,
        store = favoritesStateStore,
        readDraft = { _generateEditorState.value.inputDraft },
        persistItems = ::persistItems,
    )

    private val favoriteTreeCoordinator = FavoriteTreeCoordinator()
    val favoriteTreeUiState: StateFlow<FavoriteTreeUiState> = favoriteTreeCoordinator.state

    private val cameraRequestCoordinator = CameraRequestCoordinator()
    val cameraCaptureState: StateFlow<CameraCaptureState> = cameraRequestCoordinator.state
    private var favoriteSearchJob: Job? = null
    private var currentFavoriteSearchQuery = ""

    private val _fireworksVisible = MutableStateFlow(false)
    val fireworksVisible: StateFlow<Boolean> = _fireworksVisible.asStateFlow()

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

    fun navigateTo(route: AppRoute) {
        navigationStateCoordinator.navigateTo(route)
    }

    fun syncExternalFavorites() {
        viewModelScope.launch(Dispatchers.IO) { barcodeDataCoordinator.syncExternalFavorites() }
    }

    fun prepareMainGenerateTab() {
        _resultUiState.update { it.copy(selectedFavoriteGroup = null, returnPage = AppRoute.Generate, showingHistoryResult = false) }
        navigateTo(AppRoute.Generate)
    }

    fun updateSettingsReturnPage(route: AppRoute) {
        navigationStateCoordinator.updateSettingsReturnPage(route)
    }

    fun clearSelectedFavoriteGroup() {
        _resultUiState.update { it.copy(selectedFavoriteGroup = null) }
    }

    fun showFireworks() {
        _fireworksVisible.value = true
    }

    fun dismissFireworks() {
        _fireworksVisible.value = false
    }

    fun updateSelectedTab(index: Int) {
        navigationStateCoordinator.updateSelectedTab(index)
    }

    fun selectMainTab(index: Int) {
        val tabPages = listOf(AppRoute.Generate, AppRoute.History, AppRoute.Favorites, AppRoute.Settings)
        if (index !in tabPages.indices) return
        if (index == 3) {
            openSettings()
        } else if (_uiState.value.page != tabPages[index]) {
            if (index == 0) prepareMainGenerateTab() else navigateTo(tabPages[index])
        } else {
            updateSelectedTab(index)
        }
    }

    fun openSettings() {
        if (_uiState.value.page == AppRoute.Settings) {
            updateSelectedTab(3)
            return
        }
        updateSettingsReturnPage(mainTabPageForCurrentPage())
        navigateTo(AppRoute.Settings)
    }

    private fun mainTabPageForCurrentPage(): AppRoute = when (_uiState.value.page) {
        AppRoute.History -> AppRoute.History
        AppRoute.Favorites -> AppRoute.Favorites
        AppRoute.Settings -> AppRoute.Settings
        AppRoute.Results -> when (_resultUiState.value.returnPage) {
            AppRoute.History -> AppRoute.History
            AppRoute.Favorites -> AppRoute.Favorites
            AppRoute.Settings -> AppRoute.Settings
            else -> AppRoute.Generate
        }
        AppRoute.LanShare -> AppRoute.Settings
        else -> AppRoute.Generate
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
        viewModelScope.launch {
            val repairedExternalItems = withContext(Dispatchers.IO) {
                barcodeDataCoordinator.repairExternalFavorite(group)
            }
            var currentGroup = favoritesStateStore.groupsSnapshot().firstOrNull { it.id == group.id }
            if (currentGroup == null) {
                DebugLog.record("favorites", "open skipped groupId=${group.id} reason=group_not_loaded")
                return@launch
            }
            if (repairedExternalItems.isNotEmpty()) {
                val repairedItemIds = repairedExternalItems.map { it.id }
                favoritesStateStore.edit {
                    items.removeAll { item -> item.id in repairedItemIds }
                    items.addAll(repairedExternalItems.map { it.copy() })
                    groups.firstOrNull { it.id == group.id }?.itemIds?.apply {
                        clear()
                        addAll(repairedItemIds)
                    }
                }
                currentGroup = currentGroup.copy(itemIds = repairedItemIds.toMutableList())
                publishDataState()
            }
            DebugLog.record(
                "favorites",
                "open start groupId=${currentGroup.id} name=${currentGroup.name} cachedItemIds=${currentGroup.itemIds.size} destination=$destination",
            )
            if (currentGroup.itemIds.isEmpty()) {
                val groupId = currentGroup.id
                val loadedIds = withContext(Dispatchers.IO) { barcodeDataCoordinator.loadStartupGroupItemIds(groupId) }
                DebugLog.record("favorites", "group links loaded groupId=$groupId itemIds=${loadedIds.size}")
                favoritesStateStore.edit {
                    groups.firstOrNull { it.id == groupId }?.itemIds?.addAll(loadedIds)
                }
                favoritesStateStore.markGroupLinksLoaded(groupId)
                currentGroup = currentGroup.copy(itemIds = loadedIds.toMutableList())
            }
            val knownItems = favoritesStateStore.itemsSnapshot()
            val knownItemIds = knownItems.asSequence().mapTo(HashSet()) { it.id }
            val missingIds = currentGroup.itemIds.filterNot { it in knownItemIds }
            if (missingIds.isNotEmpty()) {
                val loaded = withContext(Dispatchers.IO) { barcodeDataCoordinator.loadItemsByIds(missingIds) }
                favoritesStateStore.edit { items.addAll(loaded) }
                publishDataState()
            }
            val itemsById = favoritesStateStore.itemsSnapshot().associateBy { it.id }
            val groupItems = currentGroup.itemIds.mapNotNull { id ->
                itemsById[id]
            }
            if (destination == AppRoute.Results && style != null && groupItems.isNotEmpty()) {
                withContext(Dispatchers.Default.limitedParallelism(4)) {
                    coroutineScope {
                        groupItems.map { item ->
                            async { barcodeImageRenderer.loadOrCreate(item, style, dark, density) }
                        }.awaitAll()
                    }
                }
            }
            DebugLog.record(
                "favorites",
                "open complete groupId=${currentGroup.id} linkIds=${currentGroup.itemIds.size} knownItems=${knownItems.size} missingLoaded=${missingIds.size} resultItems=${groupItems.size}",
            )
            _resultUiState.update { it.copy(selectedFavoriteGroup = currentGroup, items = groupItems, showingHistoryResult = false, returnPage = AppRoute.Favorites) }
            onLoaded?.invoke(groupItems)
            if (destination == AppRoute.Generate) {
                updateInputDraft(groupItems.map { it.text })
                _generateEditorState.update { it.copy(pendingFormat = groupItems.firstOrNull()?.format) }
            }
            navigateTo(destination)
        }
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
        _resultUiState.update { it.copy(items = batch.sortedBy { item -> item.id }, showingHistoryResult = true, returnPage = AppRoute.History) }
        navigateTo(AppRoute.Results)
    }

    fun editCurrentResult() {
        updateInputDraft(_resultUiState.value.items.map { it.text })
        _generateEditorState.update { it.copy(pendingFormat = _resultUiState.value.items.firstOrNull()?.format) }
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

    fun updateInputDraft(values: List<String>) {
        _generateEditorState.update { it.copy(inputDraft = values.toList()) }
    }

    fun updateGenerateFormat(format: String) {
        _generateEditorState.update { it.copy(formatName = format) }
    }

    fun clearPendingGenerateFormat() {
        _generateEditorState.update { it.copy(pendingFormat = null) }
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
        val result = generationCoordinator.generate(formatName, _resultUiState.value)
        result.uiState?.let {
            _resultUiState.value = it
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
        favoriteTreeCoordinator.sync(folders)
    }

    fun addCollapsedFavoriteFolders(paths: Set<String>) {
        favoriteTreeCoordinator.addCollapsed(paths)
    }

    fun toggleFavoriteFolder(path: String, folders: Set<String>) {
        favoriteTreeCoordinator.toggle(path, folders)
    }

    fun updateFavoriteSearch(expandedPaths: Set<String>, searching: Boolean) {
        favoriteTreeCoordinator.updateSearch(expandedPaths, searching)
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
        _resultUiState.update { it.copy(selectedFavoriteGroup = null) }
        navigateTo(AppRoute.Favorites)
        return true
    }

    fun updateFavoriteGroupAndPersist(groupId: Long, name: String, folder: String): Boolean {
        if (favoritesStateStore.groupsSnapshot().none { it.id == groupId }) return false
        if (!favoritesCoordinator.updateGroup(groupId, name, folder)) return false
        refreshFavoritesAfterMutation()
        _resultUiState.update {
            it.copy(selectedFavoriteGroup = favoritesStateStore.groupsSnapshot().firstOrNull { group -> group.id == groupId })
        }
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

    fun restoreExternalFavorites() {
        viewModelScope.launch {
            barcodeDataCoordinator.restoreExternalFavorites(this).await()
                .onSuccess {
                    loadPersistedData()
                    _events.emit(BarcodeEvent.Notice("已从外部收藏目录恢复收藏"))
                }
                .onFailure { error ->
                    _events.emit(BarcodeEvent.Notice("外部收藏恢复失败：${error.message ?: "没有可恢复的收藏"}"))
                }
        }
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
