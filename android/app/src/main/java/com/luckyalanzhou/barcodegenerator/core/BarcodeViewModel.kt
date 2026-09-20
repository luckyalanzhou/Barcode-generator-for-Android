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
import kotlinx.coroutines.flow.update
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import com.luckyalanzhou.barcodegenerator.NavigationRoute as AppRoute
import com.luckyalanzhou.barcodegenerator.data.LocalBarcodeFileStore
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
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
    private val favoritesCoordinator = FavoritesMutationCoordinator(
        store = favoritesStateStore,
        persistence = barcodePersistence,
        scope = viewModelScope,
    )
    private val favoritesQueryCoordinator = FavoritesQueryCoordinator(
        repository = barcodeDataCoordinator.repository,
        store = favoritesStateStore,
    )
    private val historyCoordinator = HistoryCoordinator(
        store = favoritesStateStore,
        persistItems = { items -> barcodePersistence.persistItems(viewModelScope, items) },
    )

    private val _dataState = MutableStateFlow(BarcodeDataState())
    val dataState: StateFlow<BarcodeDataState> = _dataState.asStateFlow()
    private val dataStateCoordinator = BarcodeDataStateCoordinator(
        store = favoritesStateStore,
        state = _dataState,
    )
    private val favoritesLoadCoordinator = FavoritesLoadCoordinator(
        persistence = barcodePersistence,
        store = favoritesStateStore,
        query = favoritesQueryCoordinator,
        publish = { isReady -> dataStateCoordinator.publish(isReady) },
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

    private val _fireworksVisible = MutableStateFlow(false)
    val fireworksVisible: StateFlow<Boolean> = _fireworksVisible.asStateFlow()

    private val updateCoordinator = UpdateCoordinator(updateDownloadService, updateCheckService, apkUpdateValidator, appLogger)
    val updateUiState: StateFlow<UpdateUiState> = updateCoordinator.uiState
    val updateDownloadUiState: StateFlow<UpdateDownloadUiState> = updateCoordinator.downloadUiState
    val updateEvents: SharedFlow<UpdateEvent> = updateCoordinator.events

    private val _events = MutableSharedFlow<BarcodeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BarcodeEvent> = _events.asSharedFlow()

    /** 发布只读快照，页面不会直接观察可变集合。 */
    fun publishDataState(isReady: Boolean = _dataState.value.isReady) {
        dataStateCoordinator.publish(isReady)
    }

    fun navigateTo(route: AppRoute) {
        navigationStateCoordinator.navigateTo(route)
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
        AppRoute.Favorites, AppRoute.FavoriteDetail -> AppRoute.Favorites
        AppRoute.Settings, AppRoute.BetaTestCenter -> AppRoute.Settings
        AppRoute.Results -> when (_resultUiState.value.returnPage) {
            AppRoute.History -> AppRoute.History
            AppRoute.Favorites -> AppRoute.Favorites
            AppRoute.Settings -> AppRoute.Settings
            else -> AppRoute.Generate
        }
        AppRoute.LanShare -> AppRoute.Settings
        else -> AppRoute.Generate
    }

    fun openFavoriteGroup(group: FavoriteGroup) {
        openFavoriteGroupWhenLoaded(group, AppRoute.Results)
    }

    fun openFavoriteForEditing(group: FavoriteGroup) {
        openFavoriteGroupWhenLoaded(group, AppRoute.Generate)
    }

    private fun openFavoriteGroupWhenLoaded(group: FavoriteGroup, destination: AppRoute) {
        viewModelScope.launch {
            var currentGroup = favoritesStateStore.groupsSnapshot().firstOrNull { it.id == group.id } ?: return@launch
            if (currentGroup.itemIds.isEmpty()) {
                val loadedIds = withContext(Dispatchers.IO) { barcodeDataCoordinator.loadStartupGroupItemIds(currentGroup.id) }
                favoritesStateStore.edit {
                    groups.firstOrNull { it.id == currentGroup.id }?.itemIds?.addAll(loadedIds)
                }
                currentGroup = currentGroup.copy(itemIds = loadedIds.toMutableList())
            }
            val knownItems = favoritesStateStore.itemsSnapshot()
            val missingIds = currentGroup.itemIds.filter { id -> knownItems.none { it.id == id } }
            if (missingIds.isNotEmpty()) {
                val loaded = withContext(Dispatchers.IO) { barcodeDataCoordinator.loadItemsByIds(missingIds) }
                favoritesStateStore.edit { items.addAll(loaded) }
                publishDataState()
            }
            val groupItems = currentGroup.itemIds.mapNotNull { id ->
                favoritesStateStore.itemsSnapshot().firstOrNull { it.id == id }
            }
            _resultUiState.update { it.copy(selectedFavoriteGroup = currentGroup, items = groupItems, showingHistoryResult = false, returnPage = AppRoute.Favorites) }
            updateInputDraft(groupItems.map { it.text })
            _generateEditorState.update { it.copy(pendingFormat = groupItems.firstOrNull()?.format) }
            navigateTo(destination)
        }
    }

    fun searchFavoriteContent(query: String) {
        favoriteSearchJob?.cancel()
        if (query.isBlank()) return
        favoriteSearchJob = viewModelScope.launch {
            favoritesQueryCoordinator.search(query)
            publishDataState()
        }
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
    }

    fun loadMoreFavoriteGroups() {
        viewModelScope.launch { favoritesLoadCoordinator.loadMoreFavoriteGroups() }
    }

    suspend fun importFavorites(backup: InterchangeBackup): Pair<Int, Int> {
        val counts = barcodeDataCoordinator.importFavorites(backup)
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
    }
}
