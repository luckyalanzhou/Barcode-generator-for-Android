package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.content.ContentResolver
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Comparator
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONArray
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import dagger.hilt.android.qualifiers.ApplicationContext

data class AppUiState(
    val route: AppRoute = AppRoute.Generate,
    val selectedTab: Int = 0,
    val settingsReturnPage: String = "generate",
) {
    val page: String get() = route.pageName
    val title: String get() = route.title
    val chromeVisible: Boolean get() = route.chromeVisible
}

/**
 * 条码、收藏和文件夹的统一只读快照。
 *
 * Compose 页面只读取这个快照，避免暴露 ViewModel 内部可变集合。
 */
data class BarcodeDataState(
    val items: List<CodeItem> = emptyList(),
    val groups: List<FavoriteGroup> = emptyList(),
    val folders: List<String> = emptyList(),
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

data class SystemRequestState(
    val externalActivityRequest: Int? = null,
    val permissionRequest: Int? = null,
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
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val localBarcodeFileStore by lazy { LocalBarcodeFileStore(appContext) }
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
    private val _systemRequestState = MutableStateFlow(SystemRequestState())
    val systemRequestState: StateFlow<SystemRequestState> = _systemRequestState.asStateFlow()

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
    fun publishDataState() {
        _dataState.value = BarcodeDataState(
            items = items.map { it.copy() },
            groups = favoriteGroups.map { it.copy(itemIds = it.itemIds.toMutableList()) },
            folders = favoriteFolders.toList(),
        )
    }

    fun navigateTo(page: String) {
        _uiState.update {
            val route = AppRoute.fromPage(page)
            it.copy(
                route = route,
                selectedTab = route.mainTabIndex ?: it.selectedTab,
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

    fun prewarmBarcodeImages(
        style: StyleSettings,
        formats: List<Pair<String, BarcodeFormat>>,
        dark: Boolean,
        density: Float,
    ) {
        val snapshot = (items.filter { it.inHistory || it.favorite } + favoriteGroups.flatMap { group ->
            group.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
        }).distinctBy { it.id }.map { it.copy() }
        val width = style.barWidth.toInt().coerceIn(120, 360)
        val height = style.barHeight.coerceIn(30, 150).coerceAtLeast(1)
        val textSize = style.textSize.coerceIn(10f, 24f)
        val showFormat = style.showFormat
        viewModelScope.launch(Dispatchers.Default) {
            snapshot.forEach { item ->
                val key = localBarcodeFileStore.imageKey(item, width, height, textSize, showFormat, dark)
                if (localBarcodeFileStore.readImage(key) == null) {
                    val encoded = encodeCachedBarcode(item.text, formats.firstOrNull { it.first == item.format }?.second ?: BarcodeFormat.CODE_128, style, dark, density)
                    if (encoded != null) {
                        val image = if (item.format == "Code 128-B") {
                            addBarcodeQuietZoneCached(trimBarcodeCached(encoded), if (dark) Color.WHITE else Color.TRANSPARENT)
                        } else encoded
                        localBarcodeFileStore.writeImage(key, image)
                    }
                }
                yield()
            }
        }
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
        _systemRequestState.update { it.copy(externalActivityRequest = requestCode) }
    }

    fun consumeExternalActivityRequest(): Int {
        val requestCode = _systemRequestState.value.externalActivityRequest ?: 0
        _systemRequestState.update { it.copy(externalActivityRequest = null) }
        return requestCode
    }

    fun beginPermissionRequest(requestCode: Int) {
        _systemRequestState.update { it.copy(permissionRequest = requestCode) }
    }

    fun consumePermissionRequest(): Int {
        val requestCode = _systemRequestState.value.permissionRequest ?: 0
        _systemRequestState.update { it.copy(permissionRequest = null) }
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
        val enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            val scale = 1.35f
            val offset = -44.8f
            set(floatArrayOf(scale, 0f, 0f, 0f, offset, 0f, scale, 0f, 0f, offset, 0f, 0f, scale, 0f, offset, 0f, 0f, 0f, 1f, 0f))
        }
        Canvas(enhanced).drawBitmap(
            bitmap,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) },
        )
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(InputImage.fromBitmap(enhanced, 0))
            .addOnSuccessListener { results ->
                val normalized = results.text.map { char ->
                    when {
                        char in "Oo" && confusionMask and SettingsStore.OCR_REPLACE_O_ZERO != 0 -> '0'
                        char in "Iil" && confusionMask and SettingsStore.OCR_REPLACE_I_ONE != 0 -> '1'
                        char in "Ss" && confusionMask and SettingsStore.OCR_REPLACE_S_FIVE != 0 -> '5'
                        char in "Bb" && confusionMask and SettingsStore.OCR_REPLACE_B_EIGHT != 0 -> '8'
                        else -> char
                    }
                }.joinToString("").lines().filter { it.isNotBlank() }
                viewModelScope.launch {
                    if (normalized.isEmpty()) _events.emit(BarcodeEvent.Notice("未识别到文字，请拍摄清晰、正面的屏幕区域"))
                    else {
                        _events.emit(BarcodeEvent.RecognizedText(normalized))
                        _events.emit(BarcodeEvent.Notice("文字识别成功，已按行添加到输入框"))
                    }
                }
            }
            .addOnFailureListener {
                viewModelScope.launch { _events.emit(BarcodeEvent.Notice("文字识别失败，请重试")) }
            }
            .addOnCompleteListener {
                recognizer.close()
                enhanced.recycle()
            }
    }

    suspend fun decodeBarcode(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            MultiFormatReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
            ).text
        } catch (_: Exception) {
            null
        }
    }
    fun setStartupUpdateCheckStarted(value: Boolean) {
        _updateUiState.update { it.copy(startupCheckStarted = value) }
    }

    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://api.github.com/repos/luckyalanzhou/Barcode-generator-for-android/releases?per_page=100")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BarcodeGenerator/${BuildConfig.VERSION_NAME}")
            }
            val releases = try {
                if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub HTTP ${connection.responseCode}")
                connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
            } finally {
                connection.disconnect()
            }
            val release = (0 until releases.length())
                .mapNotNull { releases.optJSONObject(it) }
                .filter { it.optString("tag_name").startsWith(BuildConfig.UPDATE_TAG_PREFIX) }
                .maxWithOrNull(Comparator { left, right ->
                    UpdateSecurity.compareVersions(
                        parseAppVersion(left.optString("tag_name")) ?: "0.0.0",
                        parseAppVersion(right.optString("tag_name")) ?: "0.0.0",
                    )
                })
            val releaseTag = release?.optString("tag_name")?.takeIf { it.isNotBlank() }
                ?: return@withContext UpdateCheckResult.Failed("暂时无法获取更新信息")
            val apkAsset = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length())
                    .mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { asset ->
                        val assetName = asset.optString("name")
                        val isOfficialRelease = BuildConfig.UPDATE_TAG_PREFIX == "android-v" &&
                            assetName.matches(Regex("""^BarcodeGenerator[0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?\\.apk$"""))
                        val isChannelAsset = BuildConfig.UPDATE_TAG_PREFIX != "android-v" &&
                            assetName.startsWith(BuildConfig.APK_FILE_PREFIX) && assetName.endsWith(".apk", true)
                        isOfficialRelease || isChannelAsset
                    }
            }
            val downloadUrl = apkAsset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
                ?: return@withContext UpdateCheckResult.Failed("暂时无法获取更新信息")
            val latest = parseAppVersion(releaseTag)
                ?: return@withContext UpdateCheckResult.Failed("版本信息格式不正确")
            val expectedSize = apkAsset.optLong("size", 0L).takeIf { it > 0L }
            val expectedSha256 = apkAsset.optString("digest")
                .removePrefix("sha256:")
                .trim()
                .lowercase(Locale.US)
                .takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            val result = if (UpdateSecurity.compareVersions(latest, BuildConfig.VERSION_NAME) > 0) {
                UpdateCheckResult.Available(latest, downloadUrl, expectedSize, expectedSha256)
            } else {
                UpdateCheckResult.UpToDate
            }
            if (result is UpdateCheckResult.Available) {
                setAvailableUpdate(result.version, result.downloadUrl, result.expectedSize, result.expectedSha256)
            } else {
                clearAvailableUpdate()
            }
            result
        } catch (error: Exception) {
            DebugLog.record("update", "check failed", error)
            UpdateCheckResult.Failed(error.message ?: "检查更新失败，请稍后重试")
        }
    }

    private fun parseAppVersion(releaseTag: String): String? {
        val value = releaseTag.trim().removePrefix(BuildConfig.UPDATE_TAG_PREFIX).removePrefix("v")
        val parts = value.split(".")
        if (parts.size < 3 || parts.size > 4 ||
            parts.take(3).any { it.isEmpty() || it.length > 9 || it.toLongOrNull() == null }
        ) return null
        if (parts.size == 4 && (parts[3].isEmpty() || parts[3].length > 12 || parts[3].toLongOrNull() == null)) return null
        return parts.take(3).joinToString(".")
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
    ): File = withContext(Dispatchers.IO) {
        val temp = File(appContext.cacheDir, "barcode-generator-update.apk.part")
        val official = File(appContext.cacheDir, "barcode-generator-update.apk")
        var connection: HttpURLConnection? = null
        try {
            val limit = UpdateSecurity.MAX_APK_DOWNLOAD_BYTES
            require(expectedSha256 != null) { "该版本缺少 SHA-256 校验信息，无法安全更新" }
            require(expectedSize == null || expectedSize <= limit) { "更新包超过 500 MB 限制" }
            require(Uri.parse(apkUrl).scheme.equals("https", ignoreCase = true)) { "更新包必须使用 HTTPS 下载" }
            connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BarcodeGenerator/" + BuildConfig.VERSION_NAME)
            }
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            DebugLog.record("update", "download response=${connection.responseCode} contentLength=${connection.contentLengthLong}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            require(total == null || total <= limit) { "更新包超过 500 MB 限制" }
            temp.delete()
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var done = 0L
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        ensureActive()
                        require(done + count <= limit) { "更新包超过 500 MB 限制" }
                        output.write(buffer, 0, count)
                        done += count
                        if (total != null) {
                            val currentProgress = (done * 100 / total).toInt().coerceIn(0, 100)
                            setUpdateDownloadProgress(currentProgress, false, "已下载 ${currentProgress}%")
                        } else {
                            setUpdateDownloadProgress(0, true, "正在下载… ${done / 1024} KB")
                        }
                    }
                }
            }
            require(temp.isFile && temp.length() > 0L) { "APK 为空" }
            require(expectedSize == null || temp.length() == expectedSize) {
                "文件大小校验失败：${temp.length()} / $expectedSize"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val actual = temp.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) digest.update(buffer, 0, count)
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            require(actual.equals(expectedSha256, true)) { "SHA-256 校验失败" }
            official.delete()
            require(temp.renameTo(official)) { "无法保存更新文件" }
            appContext.cacheDir.listFiles()
                ?.filter { it.name.startsWith("barcode-generator-update") && it != official }
                ?.forEach { it.delete() }
            DebugLog.record("update", "download validated size=${official.length()}")
            official
        } finally {
            connection?.disconnect()
            if (!official.isFile) temp.delete()
        }
    }

    fun validateDownloadedApk(file: File) {
        val packageManager = appContext.packageManager
        val signingFlags = if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            android.content.pm.PackageManager.GET_SIGNATURES
        }
        val info = packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags)
            ?: throw IllegalStateException("无法读取 APK 信息")
        if (info.packageName != appContext.packageName) throw IllegalStateException("APK 包名与当前应用不一致")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        if (versionCode <= BuildConfig.VERSION_CODE) throw IllegalStateException("APK 版本不是当前版本的更高版本")
        val downloaded = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val installedInfo = packageManager.getPackageInfo(appContext.packageName, signingFlags)
        val installed = if (android.os.Build.VERSION.SDK_INT >= 28) installedInfo.signingInfo?.apkContentsSigners else installedInfo.signatures
        if (downloaded.isNullOrEmpty() || installed.isNullOrEmpty() ||
            downloaded.map { it.toCharsString() }.toSet() != installed.map { it.toCharsString() }.toSet()
        ) {
            throw IllegalStateException("APK 签名与当前应用不一致")
        }
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
        val itemSnapshot = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(500))
            .map { CodeItemEntity(it.id, it.text, it.format, it.createdAt, it.favorite, it.folder, it.inHistory) }
        val groupSnapshot = favoriteGroups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) }
        val groupItemSnapshot = favoriteGroups.flatMap { group ->
            group.itemIds.map { FavoriteGroupItemEntity(group.id, it) }
        }
        val folderSnapshot = favoriteFolders.filter { it.isNotBlank() }.distinct().map(::FavoriteFolderEntity)
        publishDataState()
        enqueuePersistence {
            barcodeRepository.saveAllFavorites(itemSnapshot, groupSnapshot, groupItemSnapshot, folderSnapshot)
        }
    }

    fun persistItems() {
        localBarcodeFileStore.rebuildHistory(items.map { it.copy() }.filter { it.inHistory })
        val snapshot = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(500))
            .map { CodeItemEntity(it.id, it.text, it.format, it.createdAt, it.favorite, it.folder, it.inHistory) }
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
            (barcodeRepository.loadFolders().map { it.name } + favoriteGroups.map { it.folder })
                .filter { it.isNotBlank() && it != "默认" }
                .distinct()
                .sorted()
        )
        publishDataState()
    }

    suspend fun migrateLegacyDataIfNeeded(legacyPrefs: SharedPreferences) {
        barcodeRepository.migrateLegacyDataIfNeeded(legacyPrefs)
    }

    suspend fun loadPersistedData(legacyPrefs: SharedPreferences) {
        migrateLegacyDataIfNeeded(legacyPrefs)
        loadItemsFromRepository()
        loadFavoriteGroupsFromRepository()
        loadFavoriteFoldersFromRepository()
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
        val groups = favoriteGroups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) }
        val links = favoriteGroups.flatMap { group -> group.itemIds.map { FavoriteGroupItemEntity(group.id, it) } }
        publishDataState()
        enqueuePersistence { barcodeRepository.saveFavoriteGroups(groups, links) }
    }

    fun persistFavoriteFolders() {
        val folders = favoriteFolders.filter { it.isNotBlank() }.distinct().map(::FavoriteFolderEntity)
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
