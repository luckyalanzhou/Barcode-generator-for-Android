package com.luckyalanzhou.barcodegenerator

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import android.view.Gravity
import android.view.MotionEvent
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.EncodeHintType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.security.MessageDigest
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val barcodeDisplayHandler = Handler(Looper.getMainLooper())
    private var barcodeDisplayModeActive = false
    private var barcodePreviousBrightness = -1f
    private var barcodePreviousKeepScreenOn = false
    private val barcodeDisplayTimeout = Runnable { restoreBarcodeDisplaySettings() }
    internal val composeFireworksVisible = mutableStateOf(false)
    /** 顶部主页面切换时的进入方向：右侧 Tab 为正、左侧 Tab 为负。 */
    internal var pendingPageTransitionDirection = 0
    internal val formats = listOf(
        "Code 128-B" to BarcodeFormat.CODE_128, "QR Code" to BarcodeFormat.QR_CODE,
        "Code 39" to BarcodeFormat.CODE_39, "EAN-13" to BarcodeFormat.EAN_13,
        "EAN-8" to BarcodeFormat.EAN_8, "UPC-A" to BarcodeFormat.UPC_A,
        "ITF-14" to BarcodeFormat.ITF, "Codabar" to BarcodeFormat.CODABAR
    )
    /** 兼容旧 UI/导入导出调用；实际集合由 BarcodeViewModel 持有。 */
    internal val items: MutableList<CodeItem>
        get() = viewModel.items
    internal val favoriteGroups: MutableList<FavoriteGroup>
        get() = viewModel.favoriteGroups
    internal val favoriteFolders: MutableList<String>
        get() = viewModel.favoriteFolders
    internal var inputDraft: MutableList<String>
        get() = viewModel.inputDraft
        set(value) { viewModel.inputDraft = value }
    internal var pendingGenerateFormat: String?
        get() = viewModel.pendingGenerateFormat
        set(value) { viewModel.pendingGenerateFormat = value }
    internal var generateFormatName: String
        get() = viewModel.generateFormatName
        set(value) { viewModel.generateFormatName = value }
    internal var composeGenerateTextImport: ((List<String>) -> Unit)? = null
    internal var page: String
        get() = viewModel.page
        set(value) { viewModel.page = value }
    internal var resultItems: List<CodeItem>
        get() = viewModel.resultItems
        set(value) { viewModel.resultItems = value }
    internal var showingHistoryResult: Boolean
        get() = viewModel.showingHistoryResult
        set(value) { viewModel.showingHistoryResult = value }
    internal var resultsReturnPage: String
        get() = viewModel.resultsReturnPage
        set(value) { viewModel.resultsReturnPage = value }
    internal var selectedFavoriteGroup: FavoriteGroup?
        get() = viewModel.selectedFavoriteGroup
        set(value) { viewModel.selectedFavoriteGroup = value }
    internal var collapsedFavoriteFolders: MutableSet<String>
        get() = viewModel.collapsedFavoriteFolders
        set(value) { viewModel.collapsedFavoriteFolders = value }
    internal var favoriteTreeInitialized: Boolean
        get() = viewModel.favoriteTreeInitialized
        set(value) { viewModel.favoriteTreeInitialized = value }
    internal var settingsReturnPage: String
        get() = viewModel.settingsReturnPage
        set(value) { viewModel.settingsReturnPage = value }
    internal var startupUpdateCheckStarted: Boolean
        get() = viewModel.startupUpdateCheckStarted
        set(value) { viewModel.startupUpdateCheckStarted = value }
    internal var availableUpdateUrl: String?
        get() = viewModel.availableUpdateUrl
        set(value) { viewModel.availableUpdateUrl = value }
    internal var availableUpdateExpectedSize: Long?
        get() = viewModel.availableUpdateExpectedSize
        set(value) { viewModel.availableUpdateExpectedSize = value }
    internal var availableUpdateSha256: String?
        get() = viewModel.availableUpdateSha256
        set(value) { viewModel.availableUpdateSha256 = value }
    internal var updateDialogShowing: Boolean
        get() = viewModel.updateDialogShowing
        set(value) { viewModel.updateDialogShowing = value }
    internal var updateDownloadRunning: Boolean
        get() = viewModel.updateDownloadRunning
        set(value) { viewModel.updateDownloadRunning = value }
    /** 条码结果页专用显示设置：窗口亮度 75%，最多保持亮屏 5 分钟，不修改系统全局设置。 */
    internal fun syncBarcodeDisplaySettings(isBarcodePage: Boolean) {
        if (!isBarcodePage) {
            restoreBarcodeDisplaySettings()
            return
        }
        if (!barcodeDisplayModeActive) {
            val attributes = window.attributes
            barcodePreviousBrightness = attributes.screenBrightness
            barcodePreviousKeepScreenOn = (attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
            barcodeDisplayModeActive = true
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            attributes.screenBrightness = 0.75f
            window.attributes = attributes
            barcodeDisplayHandler.postDelayed(barcodeDisplayTimeout, 5 * 60 * 1000L)
        }
    }

    /** 条码结果页每次触摸都重新获得 5 分钟亮屏时间；无操作后恢复系统熄屏规则。 */
    private fun refreshBarcodeDisplayTimeout() {
        if (page != "results") return
        syncBarcodeDisplaySettings(true)
        barcodeDisplayHandler.removeCallbacks(barcodeDisplayTimeout)
        barcodeDisplayHandler.postDelayed(barcodeDisplayTimeout, 5 * 60 * 1000L)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) refreshBarcodeDisplayTimeout()
        return super.dispatchTouchEvent(event)
    }

    private fun restoreBarcodeDisplaySettings() {
        if (!barcodeDisplayModeActive) return
        barcodeDisplayHandler.removeCallbacks(barcodeDisplayTimeout)
        if (barcodePreviousKeepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = barcodePreviousBrightness }
        barcodeDisplayModeActive = false
        barcodePreviousBrightness = -1f
    }
    // 搜索期间暂存用户原本的折叠状态；清除搜索后准确恢复。
    internal var favoriteCollapsedBeforeSearch: Set<String>? = null
    internal val composeShellRevision = mutableIntStateOf(0)
    internal var composeShellReady: Boolean = false
    internal var tabGlassDragActive = false
    // Tab 选中状态可能在布局刷新时回调；此标志防止回调再次嵌套进入 render。
    internal var isRenderingUi = false
    internal var pendingCameraRequest = REQUEST_SCAN_CAMERA
    internal var pendingCameraUri: Uri? = null
    internal var pendingCameraFile: File? = null
    internal val legacyPrefs by lazy { getSharedPreferences("barcode_app", MODE_PRIVATE) }
    @Inject
    internal lateinit var settingsStore: SettingsStore
    internal val viewModel: BarcodeViewModel by viewModels()
    internal val settingsViewModel: SettingsViewModel by viewModels()
    internal val lanShareViewModel: LanShareViewModel by viewModels()
    @Inject
    internal lateinit var generateBarcodesUseCase: GenerateBarcodesUseCase
    internal val databaseMutex = Mutex()
    // 保存任务使用独立队列，避免连续编辑时由多个 lifecycleScope 任务乱序覆盖。
    internal val persistenceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    internal val persistenceQueueLock = Any()
    internal var persistenceWriteTail: kotlinx.coroutines.Job? = null
    @Inject
    internal lateinit var database: BarcodeDatabase
    @Inject
    internal lateinit var dao: BarcodeDao
    @Inject
    internal lateinit var barcodeRepository: BarcodeRepository
    @Inject
    internal lateinit var favoritesBackupUseCase: FavoritesBackupUseCase
    /** 兼容旧代码的访问器；设置实际由 SettingsViewModel 持有。 */
    internal val style: StyleSettings
        get() = settingsViewModel.style
    internal var lanShareManagerRef: LanShareManager? = null
    @Inject
    internal lateinit var injectedLanShareManager: LanShareManager
    internal val lanShareManager: LanShareManager
        get() = lanShareManagerRef ?: retainedLanShare?.manager?.also { lanShareManagerRef = it }
            ?: injectedLanShareManager.also { lanShareManagerRef = it }
    internal var lanShareSession: LanShareSession? = null
    internal var lanShareIsHost = false
    internal var lanShareQrVisible = false
    internal var lanShareBrowserConnected = false
    internal var lanShareFiles: List<LanShareFile> = emptyList()
    internal val lanShareOwnFileIds = mutableSetOf<String>()
    internal val lanSharePreviewFiles = mutableMapOf<String, File>()
    internal val lanShareRefreshHandler = Handler(Looper.getMainLooper())
    internal var lanShareRefreshRunnable: Runnable? = null
    internal var lanShareRefreshInFlight = false
    internal var lanSharePreviewJob: kotlinx.coroutines.Job? = null
    internal val composeLanShareRevision = mutableIntStateOf(0)
    internal var composeLanShareClearInput: (() -> Unit)? = null
    internal var pendingLanDownloadId: String? = null
    internal var pendingLanUploadUri: Uri? = null
    internal var pendingLanUploadTempFile: File? = null
    internal var pendingLanUploadName: String? = null
    internal var pendingLanCameraStartedAt = 0L

    companion object {
        private data class RetainedLanShare(
            val manager: LanShareManager,
            val session: LanShareSession,
            val isHost: Boolean,
            val files: List<LanShareFile>,
            val ownFileIds: Set<String>
        )
        private var retainedLanShare: RetainedLanShare? = null
        const val REQUEST_CAMERA_PERMISSION = 42
        const val REQUEST_SCAN_CAMERA = 43
        const val REQUEST_TEXT_CAMERA = 45
         const val REQUEST_FAVORITES_EXPORT = 49
         const val REQUEST_FAVORITES_IMPORT = 50
         const val REQUEST_LAN_SHARE_SCAN = 51
        const val REQUEST_LAN_SHARE_UPLOAD = 52
         const val REQUEST_LAN_SHARE_DOWNLOAD = 53
        const val REQUEST_LAN_SHARE_CAPTURE = 54
        const val REQUEST_LAN_SHARE_GALLERY_PERMISSION = 56
        const val REQUEST_LAN_SHARE_FILE_PERMISSION = 57
        const val MAX_HISTORY_ITEMS = 500
        const val MAX_FAVORITE_GROUPS = 200
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleAppBackPressed()
        })
        DebugLog.initialize(applicationContext)
        DebugLog.record("lifecycle", "onCreate version=${BuildConfig.VERSION_NAME} package=$packageName")
        // 统一由 buildShell 的内边距处理系统栏，避免 Android 15 主题重建时重复 inset 导致页面压缩下移。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            var startupError: Throwable? = null
            try {
                withContext(Dispatchers.IO) {
                    settingsStore.load()
                    migrateLegacySettingsIfNeeded()
                    migrateLegacyDataIfNeeded()
                    loadItemsOnIo()
                    loadFavoriteGroupsOnIo()
                    loadFavoriteFoldersOnIo()
                }
            } catch (error: Exception) {
                // 数据层损坏或升级失败不能让 Activity 直接因未处理协程异常闪退；
                // 保留默认内存状态，先让用户进入应用并看到可恢复的提示。
                startupError = error
                Log.e("BarcodeGenerator", "Startup data initialization failed", error)
                DebugLog.record("startup", "data initialization failed", error)
            }
            try {
                settingsViewModel.initialize(loadStyle(), settingsStore.getOcrConfusionReplacementMask())
                restoreLanShareAfterConfigurationChange()
                // 先应用已保存的外观，再创建动态控件，避免首次进入仍显示浅色页面。
                applyAppearance()
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                buildComposeShell()
                applyAppearance()
                if (state != null && page == "generate") {
                    page = state.getString("page", "generate") ?: "generate"
                    settingsReturnPage = state.getString("settings_return_page", "generate") ?: "generate"
                    startupUpdateCheckStarted = state.getBoolean("startup_update_check_started", false)
                }
                render()
            } catch (error: Exception) {
                startupError = startupError ?: error
                Log.e("BarcodeGenerator", "Startup UI initialization failed", error)
                DebugLog.record("startup", "UI initialization failed", error)
            }
            if (!composeShellReady) {
                setContentView(androidx.compose.ui.platform.ComposeView(this@MainActivity).apply {
                    setContent {
                        androidx.compose.material3.Text("应用初始化失败，请重新打开应用")
                    }
                })
                return@launch
            }
            startupError?.let {
                window.decorView.post { showIos26NoticeDialog("数据加载失败，已使用默认页面启动") }
            }
            window.decorView.post {
                if (!startupUpdateCheckStarted) {
                    startupUpdateCheckStarted = true
                    checkForUpdates(silent = true)
                }
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        // AppCompat 切换浅/深色会重建 Activity；在新窗口完成恢复后再校准一次系统栏。
        if (composeShellReady) syncSystemBars()
    }

    override fun onResume() {
        super.onResume()
        val pendingPath = viewModel.pendingInstallPath ?: return
        if (android.os.Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
            viewModel.pendingInstallPath = null
            installApk(File(pendingPath))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page)
        outState.putString("settings_return_page", settingsReturnPage)
        outState.putBoolean("startup_update_check_started", startupUpdateCheckStarted)
        super.onSaveInstanceState(outState)
    }

    /** 统一的现代返回回调，保持原有页面返回路径。 */
    private fun handleAppBackPressed() {
        if (composeFireworksVisible.value) {
            dismissFireworksEasterEgg()
            return
        }
        when (page) {
            "settings" -> { page = settingsReturnPage.takeIf { it in setOf("generate", "history", "favorites", "settings") } ?: "generate"; render() }
            "betaTestCenter" -> { page = "settings"; render() }
            "lanShare" -> { closeLanShare(); page = "settings"; render() }
            "favoriteDetail" -> { page = "favorites"; render() }
            "results" -> { page = resultsReturnPage.takeIf { it in setOf("generate", "history", "favorites", "settings") } ?: "generate"; render() }
            else -> finish()
        }
    }

    override fun onDestroy() {
        restoreBarcodeDisplaySettings()
        val session = lanShareSession
        if (isChangingConfigurations && session != null) {
            stopLanShareAutoRefresh()
            retainedLanShare = RetainedLanShare(lanShareManager, session, lanShareIsHost, lanShareFiles, lanShareOwnFileIds.toSet())
        } else {
            closeLanShare()
        }
        super.onDestroy()
    }

    private fun restoreLanShareAfterConfigurationChange() {
        val retained = retainedLanShare ?: return
        lanShareManagerRef = retained.manager
        lanShareSession = retained.session
        lanShareIsHost = retained.isHost
        lanShareFiles = retained.files
        lanShareOwnFileIds.clear()
        lanShareOwnFileIds.addAll(retained.ownFileIds)
        retainedLanShare = null
        startLanShareAutoRefresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                when (pendingCameraRequest) {
                    REQUEST_LAN_SHARE_CAPTURE -> openLanShareCamera()
                    REQUEST_TEXT_CAMERA -> launchCamera(REQUEST_TEXT_CAMERA)
                    else -> launchCamera(pendingCameraRequest)
                }
            } else {
                toast("需要相机权限才能拍照识别")
            }
        } else if (requestCode == REQUEST_LAN_SHARE_GALLERY_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openLanShareGalleryPicker() else toast("需要照片权限才能选择图库照片")
        } else if (requestCode == REQUEST_LAN_SHARE_FILE_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openLanShareFilePicker() else toast("需要文件权限才能选择文件")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LAN_SHARE_UPLOAD || requestCode == REQUEST_LAN_SHARE_DOWNLOAD) {
            if (resultCode == RESULT_OK) data?.data?.let { uri ->
                if (requestCode == REQUEST_LAN_SHARE_UPLOAD) selectLanShareAttachment(uri, autoUpload = true)
                else pendingLanDownloadId?.let { id -> pendingLanDownloadId = null; downloadLanShareFile(id, uri) }
            }
            if (resultCode != RESULT_OK && requestCode == REQUEST_LAN_SHARE_DOWNLOAD) pendingLanDownloadId = null
            return
        }
        if (requestCode == REQUEST_LAN_SHARE_CAPTURE) {
            val captureUri = if (resultCode == RESULT_OK) {
                pendingCameraUri?.takeIf { pendingCameraFile?.length()?.let { size -> size > 0L } == true }
                    ?: findRecentLanCameraMedia()
            } else null
            val captureFile = pendingCameraFile
            pendingCameraUri = null
            pendingCameraFile = null
            if (captureUri != null) selectLanShareAttachment(captureUri, captureFile, autoUpload = true)
            else captureFile?.delete()
            return
        }
        if (requestCode == REQUEST_FAVORITES_EXPORT || requestCode == REQUEST_FAVORITES_IMPORT) {
            if (resultCode == RESULT_OK) data?.data?.let { uri ->
                 when (requestCode) {
                     REQUEST_FAVORITES_EXPORT -> exportFavorites(uri)
                     REQUEST_FAVORITES_IMPORT -> confirmImportFavorites(uri)
                 }
             }
            return
        }
        if (resultCode != RESULT_OK) {
            pendingCameraUri = null
            pendingCameraFile?.delete()
            pendingCameraFile = null
            return
        }
        val cameraFile = pendingCameraFile
        val bitmap = when (requestCode) {
            43, 45, 51 -> pendingCameraUri?.let { uri ->
                runCatching { contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull()
            } ?: (data?.extras?.get("data") as? Bitmap)
            44, 46 -> data?.data?.let { uri ->
                runCatching { contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull()
            }
            else -> null
        }
        if (bitmap == null) {
            pendingCameraUri = null
            pendingCameraFile = null
            cameraFile?.delete()
            return
        }
        val textBitmap = if (requestCode == 45 || requestCode == 46) prepareTextBitmap(bitmap, cameraFile) else bitmap
        pendingCameraUri = null
        cameraFile?.delete()
        pendingCameraFile = null
        when (requestCode) {
            43, 44 -> decodeBitmap(bitmap)?.let { decoded ->
                // 识别结果直接回填 Compose 生成页，避免依赖已经不再承载界面的旧 EditText。
                importRecognizedText(decoded)
                toast("条码识别成功")
            } ?: toast("未识别到条码，请更换清晰图片")
            51 -> decodeBitmap(bitmap)?.let { joinLanShareSession(it) } ?: toast("未识别到分享二维码")
            45, 46 -> recognizeText(textBitmap)
        }
    }

}
