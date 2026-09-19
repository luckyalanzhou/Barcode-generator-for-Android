package com.luckyalanzhou.barcodegenerator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Color
import android.net.Uri
import java.io.File
import kotlin.math.roundToInt
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
import kotlinx.coroutines.Job
import com.google.zxing.MultiFormatWriter
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.luckyalanzhou.barcodegenerator.ui.AppRoute
import com.luckyalanzhou.barcodegenerator.data.*
import com.luckyalanzhou.barcodegenerator.domain.*

private const val FAVORITE_GROUP_PAGE_SIZE = 100

data class AppUiState(
    val page: AppRoute = AppRoute.Generate,
    val selectedTab: Int = 0,
    val settingsReturnPage: AppRoute = AppRoute.Generate,
)

/**
 * 条码、收藏和文件夹的统一只读快照。
 *
 * Compose 页面只读取这个快照，避免暴露 ViewModel 内部可变集合。
 */
data class BarcodeDataState(
    val items: List<CodeItem> = emptyList(),
    val groups: List<FavoriteGroup> = emptyList(),
    val folders: List<String> = emptyList(),
    val isReady: Boolean = false,
)

data class GenerateEditorState(
    val inputDraft: List<String> = emptyList(),
    val pendingFormat: String? = null,
    val formatName: String = "Code 128-B",
)

data class ResultUiState(
    val items: List<CodeItem> = emptyList(),
    val showingHistoryResult: Boolean = false,
    val returnPage: AppRoute = AppRoute.Generate,
    val selectedFavoriteGroup: FavoriteGroup? = null,
)

data class FavoriteTreeUiState(
    val collapsedFolders: Set<String> = emptySet(),
    val initialized: Boolean = false,
    val collapsedBeforeSearch: Set<String>? = null,
)

data class CameraCaptureState(
    val requestCode: Int = 43,
    val outputUri: Uri? = null,
    val outputFile: File? = null,
    val startedAtMillis: Long = 0L,
)

data class UpdateUiState(
    val startupCheckStarted: Boolean = false,
    val availableVersion: String? = null,
    val availableUrl: String? = null,
    val expectedSize: Long? = null,
    val sha256: String? = null,
    val dialogShowing: Boolean = false,
    val downloadRunning: Boolean = false,
    val pendingInstallPath: String? = null,
)

data class UpdateDownloadUiState(
    val progress: Int = 0,
    val indeterminate: Boolean = false,
    val status: String = "准备下载…",
)

sealed interface UpdateEvent {
    data class DownloadReady(val filePath: String) : UpdateEvent
    data class DownloadFailed(
        val apkUrl: String,
        val expectedSize: Long?,
        val expectedSha256: String?,
        val reason: String,
    ) : UpdateEvent
}

sealed interface UpdateCheckResult {
    data class Available(
        val version: String,
        val downloadUrl: String,
        val expectedSize: Long?,
        val expectedSha256: String?,
    ) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

sealed interface BarcodeEvent {
    data class RecognizedText(val lines: List<String>) : BarcodeEvent
    data class Notice(val message: String) : BarcodeEvent
}

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val favoritesBackupRepository: FavoritesBackupRepository,
    private val generateBarcodesUseCase: GenerateBarcodesUseCase,
    private val localBarcodeFileStore: LocalBarcodeFileStore,
    private val updateDownloadService: UpdateDownloadService,
    private val updateCheckService: UpdateCheckService,
    private val ocrTextService: OcrTextService,
    private val barcodeDecodeService: BarcodeDecodeService,
    private val legacyBarcodeDataMigrator: LegacyBarcodeDataMigrator,
    private val apkUpdateValidator: ApkUpdateValidator,
    private val appLogger: AppLogger,
) : ViewModel() {
    private val barcodePersistence = BarcodePersistenceCoordinator(
        barcodeRepository = barcodeRepository,
        legacyBarcodeDataMigrator = legacyBarcodeDataMigrator,
    )
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /** 条码与收藏的内部工作集合；对外只发布不可变状态快照。 */
    private val items = mutableListOf<CodeItem>()
    private val favoriteGroups = mutableListOf<FavoriteGroup>()
    private val favoriteFolders = mutableListOf<String>()
    private var favoriteGroupsOffset = 0
    private var hasMoreFavoriteGroups = false
    private var loadingMoreFavoriteGroups = false
    private val favoritesCoordinator = FavoritesCoordinator(
        items = items,
        groups = favoriteGroups,
        folders = favoriteFolders,
        persistence = barcodePersistence,
        scope = viewModelScope,
        publish = ::publishDataState,
    )

    private val _dataState = MutableStateFlow(BarcodeDataState())
    val dataState: StateFlow<BarcodeDataState> = _dataState.asStateFlow()

    private val _generateEditorState = MutableStateFlow(GenerateEditorState())
    val generateEditorState: StateFlow<GenerateEditorState> = _generateEditorState.asStateFlow()

    private val _resultUiState = MutableStateFlow(ResultUiState())
    val resultUiState: StateFlow<ResultUiState> = _resultUiState.asStateFlow()

    private val generationCoordinator = BarcodeGenerationCoordinator(
        useCase = generateBarcodesUseCase,
        items = items,
        readDraft = { _generateEditorState.value.inputDraft },
        readResult = { _resultUiState.value },
        updateResult = { _resultUiState.value = it },
        persistItems = ::persistItems,
        navigate = ::navigateTo,
    )

    private val _favoriteTreeUiState = MutableStateFlow(FavoriteTreeUiState())
    val favoriteTreeUiState: StateFlow<FavoriteTreeUiState> = _favoriteTreeUiState.asStateFlow()

    private val _cameraCaptureState = MutableStateFlow(CameraCaptureState())
    val cameraCaptureState: StateFlow<CameraCaptureState> = _cameraCaptureState.asStateFlow()
    // 平台 ActivityResult 回调的关联码是一次性桥接状态，不参与 Compose UI 渲染。
    private var pendingExternalActivityRequest: Int? = null
    private var pendingPermissionRequest: Int? = null
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
        _dataState.value = BarcodeDataState(
            items = items.map { it.copy() },
            groups = favoriteGroups.map { it.copy(itemIds = it.itemIds.toMutableList()) },
            folders = favoriteFolders.toList(),
            isReady = isReady,
        )
    }

    fun navigateTo(route: AppRoute) {
        _uiState.update {
            it.copy(
                page = route,
                selectedTab = when (route.mainTabIndex) {
                    0, 1, 2, 3 -> route.mainTabIndex
                    else -> it.selectedTab
                },
            )
        }
    }

    fun prepareMainGenerateTab() {
        _resultUiState.update { it.copy(selectedFavoriteGroup = null, returnPage = AppRoute.Generate, showingHistoryResult = false) }
        navigateTo(AppRoute.Generate)
    }

    fun updateSettingsReturnPage(route: AppRoute) {
        _uiState.update { it.copy(settingsReturnPage = route) }
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
        if (index !in 0..3) return
        _uiState.update { it.copy(selectedTab = index) }
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
            val currentGroup = favoriteGroups.firstOrNull { it.id == group.id } ?: return@launch
            if (currentGroup.itemIds.isEmpty()) {
                val loadedIds = withContext(Dispatchers.IO) { barcodeRepository.loadGroupItemIds(currentGroup.id) }
                currentGroup.itemIds.addAll(loadedIds)
            }
            val missingIds = currentGroup.itemIds.filter { id -> items.none { it.id == id } }
            if (missingIds.isNotEmpty()) {
                val loaded = withContext(Dispatchers.IO) { barcodeRepository.loadItemsByIds(missingIds) }
                items.addAll(loaded)
                publishDataState()
            }
            val groupItems = currentGroup.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
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
            val matchingGroupIds = withContext(Dispatchers.IO) { barcodeRepository.searchFavoriteGroupIds(query) }
            val knownGroupIds = favoriteGroups.mapTo(HashSet()) { it.id }
            if (matchingGroupIds.isNotEmpty()) {
                val missingGroups = withContext(Dispatchers.IO) {
                    barcodeRepository.loadFavoriteGroupsByIds(matchingGroupIds.filterNot { it in knownGroupIds })
                }
                favoriteGroups.addAll(missingGroups)
            }
            val matches = withContext(Dispatchers.IO) { barcodeRepository.searchFavoriteItems(query) }
            val knownIds = items.mapTo(HashSet()) { it.id }
            items.addAll(matches.filterNot { it.id in knownIds })
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
        val ids = batch.map { it.id }.toSet()
        items.filter { it.id in ids }.forEach { it.inHistory = false }
        persistItems()
    }

    /** 结果页图片缓存的唯一入口；Compose 不直接访问文件缓存或执行条码生成。 */
    fun loadOrCreateBarcodeImage(
        item: CodeItem,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
    ): Bitmap? {
        val width = style.barWidth.toInt().coerceIn(120, 360)
        val height = style.barHeight.coerceIn(30, 150).coerceAtLeast(1)
        val textSize = style.textSize.coerceIn(10f, 24f)
        val showFormat = style.showFormat
        val key = localBarcodeFileStore.imageKey(item, width, height, textSize, showFormat, dark)
        localBarcodeFileStore.readImage(key)?.let { return it }
        val format = barcodeFormats.firstOrNull { it.first == item.format }?.second ?: BarcodeFormat.CODE_128
        val encoded = encodeCachedBarcode(item.text, format, style, dark, density) ?: return null
        val image = if (item.format == "Code 128-B") {
            addBarcodeQuietZoneCached(trimBarcodeCached(encoded), if (dark) Color.WHITE else Color.TRANSPARENT)
        } else encoded
        localBarcodeFileStore.writeImage(key, image)
        return image
    }

    fun createBarcodeImage(
        text: String,
        format: BarcodeFormat,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean = true,
    ): Bitmap? {
        val encoded = encodeCachedBarcode(text, format, style, dark, density, withBackground) ?: return null
        return if (format == BarcodeFormat.CODE_128) {
            addBarcodeQuietZoneCached(
                trimBarcodeCached(encoded),
                if (withBackground) Color.WHITE else Color.TRANSPARENT,
            )
        } else encoded
    }

    private fun encodeCachedBarcode(
        text: String,
        format: BarcodeFormat,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean = true,
    ): Bitmap? = runCatching {
        val code128 = format == BarcodeFormat.CODE_128
        val width = if (code128) (style.barWidth.roundToInt().coerceIn(120, 360) * density).roundToInt().coerceAtLeast(1) else 500
        val barcodeHeight = if (code128) (style.barHeight.coerceIn(30, 150) * density).roundToInt().coerceAtLeast(1)
        else if (format == BarcodeFormat.QR_CODE) 500 else 200
        val matrix = MultiFormatWriter().encode(text, format, width, barcodeHeight, mapOf(EncodeHintType.MARGIN to 0))
        val paint = Paint().apply { color = if (dark) Color.BLACK else style.barColor }
        Bitmap.createBitmap(width, barcodeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(if (withBackground) (if (dark) Color.WHITE else style.bgColor) else Color.TRANSPARENT)
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                if (matrix[x, y]) canvas.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), paint)
            }
        }
    }.getOrNull()

    private fun trimBarcodeCached(source: Bitmap): Bitmap {
        var left = source.width
        var right = -1
        for (x in 0 until source.width) {
            var hasBar = false
            for (y in 0 until source.height) {
                val pixel = source.getPixel(x, y)
                val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (Color.alpha(pixel) > 0 && luminance < 200) { hasBar = true; break }
            }
            if (hasBar) { left = minOf(left, x); right = maxOf(right, x) }
        }
        return if (right >= left) Bitmap.createBitmap(source, left, 0, right - left + 1, source.height) else source
    }

    private fun addBarcodeQuietZoneCached(source: Bitmap, backgroundColor: Int): Bitmap {
        val quiet = maxOf(8, source.height / 4)
        return Bitmap.createBitmap(source.width + quiet * 2, source.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(backgroundColor); drawBitmap(source, quiet.toFloat(), 0f, Paint()) }
        }
    }

    fun createFavoriteFolder(path: String): Boolean {
        if (path.isBlank() || path in favoriteFolders) return false
        favoriteFolders.add(path)
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
        _cameraCaptureState.update { it.copy(requestCode = requestCode) }
    }

    fun setCameraOutput(uri: Uri?, file: File?) {
        _cameraCaptureState.update { it.copy(outputUri = uri, outputFile = file) }
    }

    fun markCameraCaptureStarted(nowMillis: Long = System.currentTimeMillis()) {
        _cameraCaptureState.update { it.copy(startedAtMillis = nowMillis) }
    }

    fun clearCameraOutput(): CameraCaptureState {
        val current = _cameraCaptureState.value
        _cameraCaptureState.update { it.copy(outputUri = null, outputFile = null, startedAtMillis = 0L) }
        return current
    }

    fun beginExternalActivityRequest(requestCode: Int) {
        pendingExternalActivityRequest = requestCode
    }

    fun consumeExternalActivityRequest(): Int {
        val requestCode = pendingExternalActivityRequest ?: 0
        pendingExternalActivityRequest = null
        return requestCode
    }

    fun beginPermissionRequest(requestCode: Int) {
        pendingPermissionRequest = requestCode
    }

    fun consumePermissionRequest(): Int {
        val requestCode = pendingPermissionRequest ?: 0
        pendingPermissionRequest = null
        return requestCode
    }

    fun generateBarcodes(formatName: String): GenerateBarcodesUseCase.Output {
        return generationCoordinator.generate(formatName)
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
        val validFolders = folders.filter { it.isNotBlank() }.toSet()
        val current = _favoriteTreeUiState.value
        val nextCollapsed = if (!current.initialized) {
            validFolders
        } else {
            current.collapsedFolders.intersect(validFolders)
        }
        val next = current.copy(
            collapsedFolders = nextCollapsed,
            initialized = true,
        )
        if (next != current) _favoriteTreeUiState.value = next
    }

    fun addCollapsedFavoriteFolders(paths: Set<String>) {
        if (paths.isEmpty()) return
        val current = _favoriteTreeUiState.value
        _favoriteTreeUiState.value = current.copy(collapsedFolders = current.collapsedFolders + paths)
    }

    fun toggleFavoriteFolder(path: String, folders: Set<String>) {
        val current = _favoriteTreeUiState.value
        val nextCollapsed = current.collapsedFolders.toMutableSet()
        if (path in nextCollapsed) {
            nextCollapsed.remove(path)
        } else {
            nextCollapsed.addAll(folders.filter { it == path || it.startsWith("$path/") })
        }
        _favoriteTreeUiState.value = current.copy(collapsedFolders = nextCollapsed)
    }

    fun updateFavoriteSearch(expandedPaths: Set<String>, searching: Boolean) {
        val current = _favoriteTreeUiState.value
        if (searching) {
            val before = current.collapsedBeforeSearch ?: current.collapsedFolders
            val nextCollapsed = current.collapsedFolders - expandedPaths
            _favoriteTreeUiState.value = current.copy(
                collapsedFolders = nextCollapsed,
                collapsedBeforeSearch = before,
            )
        } else {
            val restored = current.collapsedBeforeSearch ?: current.collapsedFolders
            _favoriteTreeUiState.value = current.copy(
                collapsedFolders = restored,
                collapsedBeforeSearch = null,
            )
        }
    }

    fun renameFavoriteFolder(path: String, renamedPath: String) {
        favoritesCoordinator.renameFolder(path, renamedPath)
    }

    fun deleteFavoriteFolder(path: String) = favoritesCoordinator.deleteFolder(path)

    fun deleteFavoriteGroup(groupId: Long) = favoritesCoordinator.deleteGroup(groupId)

    fun deleteBarcodeItem(itemId: Long) = favoritesCoordinator.deleteItem(itemId)

    fun updateBarcodeItem(itemId: Long, text: String, format: String) = favoritesCoordinator.updateItem(itemId, text, format)

    fun renameFavoriteFolderAndPersist(path: String, renamedPath: String) {
        favoritesCoordinator.renameFolderAndPersist(path, renamedPath)
    }

    fun deleteFavoriteFolderAndPersist(path: String) {
        favoritesCoordinator.deleteFolderAndPersist(path)
    }

    fun renameFavoriteGroupAndPersist(groupId: Long, name: String) {
        favoritesCoordinator.renameGroupAndPersist(groupId, name)
    }

    fun moveFavoriteGroupAndPersist(groupId: Long, folder: String) {
        favoritesCoordinator.moveGroupAndPersist(groupId, folder)
    }

    fun deleteFavoriteGroupAndPersist(groupId: Long) {
        favoritesCoordinator.deleteGroupAndPersist(groupId)
    }

    fun clearFavoritesAndPersist() {
        favoritesCoordinator.clearFavoritesAndPersist()
    }

    fun clearHistoryAndPersist() {
        favoritesCoordinator.clearHistoryAndPersist()
    }

    fun saveResultAsFavorite(
        resultItemIds: List<Long>,
        editingGroupId: Long?,
        targetGroupId: Long?,
        folder: String,
        name: String,
    ): Boolean {
        if (!favoritesCoordinator.saveResultAsFavorite(resultItemIds, editingGroupId, targetGroupId, folder, name)) return false
        _resultUiState.update { it.copy(selectedFavoriteGroup = null) }
        navigateTo(AppRoute.Favorites)
        return true
    }

    fun updateFavoriteGroupAndPersist(groupId: Long, name: String, folder: String): Boolean {
        val group = favoriteGroups.firstOrNull { it.id == groupId } ?: return false
        if (!favoritesCoordinator.updateGroup(groupId, name, folder)) return false
        _resultUiState.update { it.copy(selectedFavoriteGroup = group) }
        return true
    }

    fun persistAllFavorites() {
        favoritesCoordinator.persistAllFavorites()
    }

    fun persistItems() {
        favoritesCoordinator.persistItems()
    }

    suspend fun loadItemsFromRepository() {
        items.clear()
        items.addAll(barcodeRepository.loadItems().map {
            CodeItem(it.id, it.text, it.format, it.createdAt, it.favorite, it.folder.takeUnless { folder -> folder == "默认" } ?: "", it.inHistory)
        })
        publishDataState()
    }

    suspend fun loadFavoriteGroupsFromRepository() {
        favoriteGroups.clear()
        val groups = barcodeRepository.loadGroups()
        val itemIds = barcodeRepository.loadGroupItems().groupBy { it.groupId }
        favoriteGroups.addAll(groups.map { group ->
            FavoriteGroup(
                group.id,
                group.folder.takeUnless { it == "默认" } ?: "",
                group.name,
                group.savedAt,
                itemIds[group.id].orEmpty().map { it.itemId }.toMutableList(),
            )
        })
        favoriteGroupsOffset = favoriteGroups.size
        hasMoreFavoriteGroups = false
        publishDataState()
    }

    suspend fun loadFavoriteFoldersFromRepository() {
        favoriteFolders.clear()
        favoriteFolders.addAll(
            (barcodeRepository.loadFolders() + favoriteGroups.map { it.folder })
                .filter { it.isNotBlank() && it != "默认" }
                .distinct()
                .sorted()
        )
        publishDataState()
    }

    suspend fun loadPersistedData() {
        _dataState.value = _dataState.value.copy(isReady = false)
        val loaded = barcodePersistence.load()
        items.clear()
        items.addAll(loaded.items)
        favoriteGroups.clear()
        favoriteGroups.addAll(loaded.groups)
        favoriteGroupsOffset = favoriteGroups.size
        hasMoreFavoriteGroups = loaded.hasMoreGroups
        favoriteFolders.clear()
        favoriteFolders.addAll(loaded.folders)
        publishDataState(isReady = true)
    }

    fun loadMoreFavoriteGroups() {
        if (!hasMoreFavoriteGroups || loadingMoreFavoriteGroups) return
        loadingMoreFavoriteGroups = true
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    barcodeRepository.loadFavoriteGroupPage(FAVORITE_GROUP_PAGE_SIZE, favoriteGroupsOffset)
                }
                val knownIds = favoriteGroups.mapTo(HashSet()) { it.id }
                favoriteGroups.addAll(page.filterNot { it.id in knownIds })
                favoriteGroupsOffset += page.size
                hasMoreFavoriteGroups = page.size == FAVORITE_GROUP_PAGE_SIZE
                publishDataState()
            } finally {
                loadingMoreFavoriteGroups = false
            }
        }
    }

    suspend fun importFavorites(backup: InterchangeBackup): Pair<Int, Int> {
        val counts = favoritesBackupRepository.import(backup)
        loadItemsFromRepository()
        loadFavoriteGroupsFromRepository()
        loadFavoriteFoldersFromRepository()
        return counts
    }

    suspend fun exportFavorites(): ByteArray = favoritesBackupRepository.export()

    fun restoreFavorites(bytes: ByteArray): InterchangeBackup = favoritesBackupRepository.restore(bytes)

    fun persistFavoriteGroups() {
        favoritesCoordinator.persistGroups()
    }

    fun persistFavoriteFolders() {
        favoritesCoordinator.persistFolders()
    }
}
