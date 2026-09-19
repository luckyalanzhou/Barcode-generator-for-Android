package com.luckyalanzhou.barcodegenerator

import android.content.ContentResolver
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import com.google.zxing.MultiFormatWriter
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class AppUiState(
    val page: String = "generate",
    val selectedTab: Int = 0,
    val settingsReturnPage: String = "generate",
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
    val returnPage: String = "generate",
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
    private val favoritesBackupUseCase: FavoritesBackupUseCase,
    private val generateBarcodesUseCase: GenerateBarcodesUseCase,
    private val localBarcodeFileStore: LocalBarcodeFileStore,
    private val updateDownloadService: UpdateDownloadService,
    private val updateCheckService: UpdateCheckService,
    private val ocrTextService: OcrTextService,
    private val barcodeDecodeService: BarcodeDecodeService,
    private val legacyBarcodeDataMigrator: LegacyBarcodeDataMigrator,
    private val apkUpdateValidator: ApkUpdateValidator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /** 条码与收藏的内部工作集合；对外只发布不可变状态快照。 */
    private val items = mutableListOf<CodeItem>()
    private val favoriteGroups = mutableListOf<FavoriteGroup>()
    private val favoriteFolders = mutableListOf<String>()

    private val _dataState = MutableStateFlow(BarcodeDataState())
    val dataState: StateFlow<BarcodeDataState> = _dataState.asStateFlow()

    private val _generateEditorState = MutableStateFlow(GenerateEditorState())
    val generateEditorState: StateFlow<GenerateEditorState> = _generateEditorState.asStateFlow()

    private val _resultUiState = MutableStateFlow(ResultUiState())
    val resultUiState: StateFlow<ResultUiState> = _resultUiState.asStateFlow()

    private val _favoriteTreeUiState = MutableStateFlow(FavoriteTreeUiState())
    val favoriteTreeUiState: StateFlow<FavoriteTreeUiState> = _favoriteTreeUiState.asStateFlow()

    private val _cameraCaptureState = MutableStateFlow(CameraCaptureState())
    val cameraCaptureState: StateFlow<CameraCaptureState> = _cameraCaptureState.asStateFlow()
    // 平台 ActivityResult 回调的关联码是一次性桥接状态，不参与 Compose UI 渲染。
    private var pendingExternalActivityRequest: Int? = null
    private var pendingPermissionRequest: Int? = null

    private val _fireworksVisible = MutableStateFlow(false)
    val fireworksVisible: StateFlow<Boolean> = _fireworksVisible.asStateFlow()

    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    private val _updateDownloadUiState = MutableStateFlow(UpdateDownloadUiState())
    val updateDownloadUiState: StateFlow<UpdateDownloadUiState> = _updateDownloadUiState.asStateFlow()

    private val _updateEvents = MutableSharedFlow<UpdateEvent>(extraBufferCapacity = 4)
    val updateEvents: SharedFlow<UpdateEvent> = _updateEvents.asSharedFlow()

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

    fun navigateTo(page: String) {
        val normalizedPage = when (page) {
            "generate", "history", "favorites", "settings", "favoriteDetail",
            "results", "lanShare", "betaTestCenter" -> page
            else -> "generate"
        }
        _uiState.update {
            it.copy(
                page = normalizedPage,
                selectedTab = when (normalizedPage) {
                    "generate" -> 0
                    "history" -> 1
                    "favorites" -> 2
                    "settings" -> 3
                    else -> it.selectedTab
                },
            )
        }
    }

    fun prepareMainGenerateTab() {
        _resultUiState.update { it.copy(selectedFavoriteGroup = null, returnPage = "generate", showingHistoryResult = false) }
        navigateTo("generate")
    }

    fun updateSettingsReturnPage(page: String) {
        _uiState.update { it.copy(settingsReturnPage = page) }
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
        val tabPages = listOf("generate", "history", "favorites", "settings")
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
        if (_uiState.value.page == "settings") {
            updateSelectedTab(3)
            return
        }
        updateSettingsReturnPage(mainTabPageForCurrentPage())
        navigateTo("settings")
    }

    private fun mainTabPageForCurrentPage(): String = when (_uiState.value.page) {
        "history" -> "history"
        "favorites", "favoriteDetail" -> "favorites"
        "settings", "betaTestCenter" -> "settings"
        "results" -> when (_resultUiState.value.returnPage) {
            "history" -> "history"
            "favorites" -> "favorites"
            "settings" -> "settings"
            else -> "generate"
        }
        "lanShare" -> "settings"
        else -> "generate"
    }

    fun openFavoriteGroup(group: FavoriteGroup) {
        val groupItems = group.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
        _resultUiState.update { it.copy(selectedFavoriteGroup = group, items = groupItems, showingHistoryResult = false, returnPage = "favorites") }
        updateInputDraft(groupItems.map { it.text })
        _generateEditorState.update { it.copy(pendingFormat = groupItems.firstOrNull()?.format) }
        navigateTo("results")
    }

    fun openFavoriteForEditing(group: FavoriteGroup) {
        val groupItems = group.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
        _resultUiState.update { it.copy(selectedFavoriteGroup = group, items = groupItems, showingHistoryResult = false, returnPage = "favorites") }
        updateInputDraft(groupItems.map { it.text })
        _generateEditorState.update { it.copy(pendingFormat = groupItems.firstOrNull()?.format) }
        navigateTo("generate")
    }

    fun openHistoryResult(batch: List<CodeItem>) {
        _resultUiState.update { it.copy(items = batch.sortedBy { item -> item.id }, showingHistoryResult = true, returnPage = "history") }
        navigateTo("results")
    }

    fun editCurrentResult() {
        updateInputDraft(_resultUiState.value.items.map { it.text })
        _generateEditorState.update { it.copy(pendingFormat = _resultUiState.value.items.firstOrNull()?.format) }
        navigateTo("generate")
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
        val result = generateBarcodesUseCase.execute(_generateEditorState.value.inputDraft, formatName, items)
        if (!result.isValid) return result

        val currentResult = _resultUiState.value
        val editingFavorite = currentResult.selectedFavoriteGroup?.takeIf { currentResult.returnPage == "favorites" }
        val generated = result.items
        items.addAll(0, generated)
        persistItems()
        _resultUiState.update {
            it.copy(
                items = generated,
                selectedFavoriteGroup = editingFavorite,
                showingHistoryResult = false,
                returnPage = if (editingFavorite != null) "favorites" else "generate",
            )
        }
        navigateTo("results")
        return result
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
    fun setStartupUpdateCheckStarted(value: Boolean) {
        _updateUiState.update { it.copy(startupCheckStarted = value) }
    }

    suspend fun checkForUpdates(): UpdateCheckResult = updateCheckService.check().also { result ->
        if (result is UpdateCheckResult.Available) setAvailableUpdate(result.version, result.downloadUrl, result.expectedSize, result.expectedSha256)
        else clearAvailableUpdate()
    }

    fun setAvailableUpdate(version: String?, url: String?, expectedSize: Long?, sha256: String?) {
        _updateUiState.update {
            it.copy(
                availableVersion = version,
                availableUrl = url,
                expectedSize = expectedSize,
                sha256 = sha256,
            )
        }
    }

    fun clearAvailableUpdate() {
        setAvailableUpdate(null, null, null, null)
    }

    fun setUpdateDialogShowing(value: Boolean) {
        _updateUiState.update { it.copy(dialogShowing = value) }
    }

    fun setUpdateDownloadRunning(value: Boolean) {
        _updateUiState.update { it.copy(downloadRunning = value) }
    }

    fun resetUpdateDownloadState() {
        _updateDownloadUiState.value = UpdateDownloadUiState()
    }

    fun setUpdateDownloadProgress(progress: Int, indeterminate: Boolean, status: String) {
        _updateDownloadUiState.value = UpdateDownloadUiState(
            progress = progress.coerceIn(0, 100),
            indeterminate = indeterminate,
            status = status,
        )
    }

    suspend fun downloadUpdate(
        apkUrl: String,
        expectedSize: Long?,
        expectedSha256: String?,
    ): File = updateDownloadService.download(apkUrl, expectedSize, expectedSha256) { progress, indeterminate, status ->
        setUpdateDownloadProgress(progress, indeterminate, status)
    }

    fun validateDownloadedApk(file: File) {
        apkUpdateValidator.validate(file)
    }

    fun startUpdateDownload(apkUrl: String, expectedSize: Long?, expectedSha256: String?) {
        if (_updateUiState.value.downloadRunning) return
        val generation = ++updateDownloadGeneration
        resetUpdateDownloadState()
        setUpdateDownloadRunning(true)
        updateDownloadJob = viewModelScope.launch {
            try {
                val official = downloadUpdate(apkUrl, expectedSize, expectedSha256)
                _updateEvents.emit(UpdateEvent.DownloadReady(official.absolutePath))
            } catch (error: CancellationException) {
                // 用户取消下载时不显示失败提示。
            } catch (error: Exception) {
                DebugLog.record("update", "download failed", error)
                _updateEvents.emit(
                    UpdateEvent.DownloadFailed(
                        apkUrl = apkUrl,
                        expectedSize = expectedSize,
                        expectedSha256 = expectedSha256,
                        reason = error.message ?: "未知错误",
                    )
                )
            } finally {
                if (updateDownloadGeneration == generation) {
                    setUpdateDownloadRunning(false)
                    updateDownloadJob = null
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadGeneration++
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        setUpdateDownloadRunning(false)
    }
    private var updateDownloadGeneration: Long = 0L
    private var updateDownloadJob: Job? = null
    fun setPendingInstallPath(path: String?) {
        _updateUiState.update { it.copy(pendingInstallPath = path) }
    }

    fun takePendingInstallPath(): String? {
        val path = _updateUiState.value.pendingInstallPath
        _updateUiState.update { it.copy(pendingInstallPath = null) }
        return path
    }
    private val persistenceLock = Any()
    private var persistenceWriteTail: Job? = null

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
        favoriteGroups
            .filter { it.folder == path || it.folder.startsWith("$path/") }
            .forEach { group ->
                group.folder = if (group.folder == path) renamedPath
                else renamedPath + group.folder.removePrefix(path)
            }
        favoriteFolders
            .filter { it == path || it.startsWith("$path/") }
            .toList()
            .forEach { old ->
                favoriteFolders.remove(old)
                favoriteFolders.add(if (old == path) renamedPath else renamedPath + old.removePrefix(path))
            }
        publishDataState()
    }

    fun deleteFavoriteFolder(path: String) {
        favoriteGroups.removeAll { it.folder == path || it.folder.startsWith("$path/") }
        favoriteFolders.removeAll { it == path || it.startsWith("$path/") }
        publishDataState()
    }

    fun deleteFavoriteGroup(groupId: Long) {
        val group = favoriteGroups.firstOrNull { it.id == groupId } ?: return
        favoriteGroups.removeAll { it.id == groupId }
        items.filter { it.id in group.itemIds }
            .filter { item -> favoriteGroups.none { remaining -> item.id in remaining.itemIds } }
            .forEach { it.favorite = false }
        if (group.folder !in favoriteFolders) favoriteFolders.add(group.folder)
        publishDataState()
    }

    fun deleteBarcodeItem(itemId: Long) {
        items.removeAll { it.id == itemId }
        favoriteGroups.forEach { group -> group.itemIds.removeAll { it == itemId } }
        persistAllFavorites()
    }

    fun updateBarcodeItem(itemId: Long, text: String, format: String) {
        val item = items.firstOrNull { it.id == itemId } ?: return
        item.text = text
        item.format = format
        if (item.favorite) persistAllFavorites() else persistItems()
    }

    fun renameFavoriteFolderAndPersist(path: String, renamedPath: String) {
        renameFavoriteFolder(path, renamedPath)
        persistAllFavorites()
    }

    fun deleteFavoriteFolderAndPersist(path: String) {
        deleteFavoriteFolder(path)
        persistAllFavorites()
    }

    fun renameFavoriteGroupAndPersist(groupId: Long, name: String) {
        favoriteGroups.firstOrNull { it.id == groupId }?.name = name
        persistAllFavorites()
    }

    fun moveFavoriteGroupAndPersist(groupId: Long, folder: String) {
        val group = favoriteGroups.firstOrNull { it.id == groupId } ?: return
        group.folder = folder
        if (folder.isNotBlank() && folder !in favoriteFolders) favoriteFolders.add(folder)
        persistAllFavorites()
    }

    fun deleteFavoriteGroupAndPersist(groupId: Long) {
        deleteFavoriteGroup(groupId)
        persistAllFavorites()
    }

    fun clearFavoritesAndPersist() {
        favoriteGroups.clear()
        items.forEach { it.favorite = false; it.folder = "默认" }
        persistAllFavorites()
    }

    fun clearHistoryAndPersist() {
        items.forEach { it.inHistory = false }
        persistItems()
    }

    fun saveResultAsFavorite(
        resultItemIds: List<Long>,
        editingGroupId: Long?,
        targetGroupId: Long?,
        folder: String,
        name: String,
    ): Boolean {
        val selectedItems = items.filter { it.id in resultItemIds }
        if (selectedItems.isEmpty() || folder.isBlank() || name.isBlank()) return false

        if (editingGroupId != null && editingGroupId != targetGroupId) {
            favoriteGroups.removeAll { it.id == editingGroupId }
        }
        selectedItems.forEach {
            it.favorite = true
            it.folder = folder
        }
        if (folder !in favoriteFolders) favoriteFolders.add(folder)

        val groupId = targetGroupId ?: ((favoriteGroups.maxOfOrNull { it.id } ?: 0L) + 1L)
        val updatedGroup = FavoriteGroup(
            groupId,
            folder,
            name,
            System.currentTimeMillis(),
            selectedItems.map { it.id }.toMutableList(),
        )
        val targetIndex = favoriteGroups.indexOfFirst { it.id == groupId }
        if (targetIndex >= 0) favoriteGroups[targetIndex] = updatedGroup
        else favoriteGroups.add(0, updatedGroup)

        items.filter { it.favorite && favoriteGroups.none { group -> it.id in group.itemIds } }
            .forEach { it.favorite = false }
        persistAllFavorites()
        _resultUiState.update { it.copy(selectedFavoriteGroup = null) }
        navigateTo("favorites")
        return true
    }

    fun updateFavoriteGroupAndPersist(groupId: Long, name: String, folder: String): Boolean {
        val group = favoriteGroups.firstOrNull { it.id == groupId } ?: return false
        group.name = name
        group.folder = folder
        if (folder !in favoriteFolders) favoriteFolders.add(folder)
        _resultUiState.update { it.copy(selectedFavoriteGroup = group) }
        persistAllFavorites()
        return true
    }

    fun persistAllFavorites() {
        val favoriteFileGroups = favoriteGroups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
        val favoriteFileItems = items.map { it.copy() }
        localBarcodeFileStore.rebuildFavorites(favoriteFileGroups, favoriteFileItems)
        val itemSnapshot = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(500)).map { it.copy() }
        val groupSnapshot = favoriteGroups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
        val groupItemSnapshot = favoriteGroups.flatMap { group ->
            group.itemIds.map { FavoriteGroupItem(group.id, it) }
        }
        val folderSnapshot = favoriteFolders.filter { it.isNotBlank() }.distinct()
        publishDataState()
        enqueuePersistence {
            barcodeRepository.saveAll(BarcodeSnapshot(itemSnapshot, groupSnapshot, groupItemSnapshot, folderSnapshot))
        }
    }

    fun persistItems() {
        localBarcodeFileStore.rebuildHistory(items.map { it.copy() }.filter { it.inHistory })
        val snapshot = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(500))
            .map { it.copy() }
        publishDataState()
        enqueuePersistence { barcodeRepository.saveItems(snapshot) }
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
        legacyBarcodeDataMigrator.migrateIfNeeded()
        _dataState.value = _dataState.value.copy(isReady = false)
        val snapshot = barcodeRepository.loadSnapshot()
        val loadedGroups = snapshot.groups.map { group ->
            FavoriteGroup(
                group.id,
                group.folder.takeUnless { it == "默认" } ?: "",
                group.name,
                group.savedAt,
                snapshot.links.filter { it.groupId == group.id }.map { it.itemId }.toMutableList(),
            )
        }
        val loadedFolders = (snapshot.folders + loadedGroups.map { it.folder })
            .filter { it.isNotBlank() && it != "默认" }
            .distinct()
            .sorted()

        items.clear()
        items.addAll(snapshot.items.map {
            it.copy(folder = it.folder.takeUnless { folder -> folder == "默认" } ?: "")
        })
        favoriteGroups.clear()
        favoriteGroups.addAll(loadedGroups)
        favoriteFolders.clear()
        favoriteFolders.addAll(loadedFolders)
        publishDataState(isReady = true)
    }

    suspend fun importFavorites(backup: InterchangeBackup): Pair<Int, Int> {
        val counts = favoritesBackupUseCase.import(backup)
        loadItemsFromRepository()
        loadFavoriteGroupsFromRepository()
        loadFavoriteFoldersFromRepository()
        return counts
    }

    suspend fun exportFavorites(resolver: ContentResolver, uri: Uri) {
        favoritesBackupUseCase.export(resolver, uri)
    }

    fun restoreFavorites(resolver: ContentResolver, uri: Uri): InterchangeBackup =
        favoritesBackupUseCase.restore(resolver, uri)

    fun persistFavoriteGroups() {
        val groups = favoriteGroups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
        val links = favoriteGroups.flatMap { group -> group.itemIds.map { FavoriteGroupItem(group.id, it) } }
        publishDataState()
        enqueuePersistence { barcodeRepository.saveFavoriteGroups(groups, links) }
    }

    fun persistFavoriteFolders() {
        val folders = favoriteFolders.filter { it.isNotBlank() }.distinct()
        publishDataState()
        enqueuePersistence { barcodeRepository.saveFavoriteFolders(folders) }
    }

    private fun enqueuePersistence(write: suspend () -> Unit) {
        val next: Job
        synchronized(persistenceLock) {
            val previous = persistenceWriteTail
            next = viewModelScope.launch(Dispatchers.IO) {
                previous?.join()
                write()
            }
            persistenceWriteTail = next
        }
        next.invokeOnCompletion {
            synchronized(persistenceLock) {
                if (persistenceWriteTail === next) persistenceWriteTail = null
            }
        }
    }
}
