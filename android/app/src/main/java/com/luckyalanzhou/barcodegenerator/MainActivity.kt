package com.luckyalanzhou.barcodegenerator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import com.luckyalanzhou.barcodegenerator.ui.AppRoute
import com.luckyalanzhou.barcodegenerator.ui.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import android.util.Log
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.appcompat.app.AppCompatActivity
import java.io.File

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val barcodeDisplayHandler = Handler(Looper.getMainLooper())
    private var barcodeDisplayModeActive = false
    private var barcodePreviousBrightness = -1f
    private var barcodePreviousKeepScreenOn = false
    private val barcodeDisplayTimeout = Runnable { restoreBarcodeDisplaySettings() }
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
        if (viewModel.uiState.value.page != "results") return
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
    internal var composeShellReady: Boolean = false
    // Tab 选中状态可能在布局刷新时回调；此标志防止回调再次嵌套进入 render。
    internal val viewModel: BarcodeViewModel by viewModels()
    internal val settingsViewModel: SettingsViewModel by viewModels()
    internal val lanShareViewModel: LanShareViewModel by viewModels()
    private val externalActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        handleExternalActivityResult(viewModel.consumeExternalActivityRequest(), result.resultCode, result.data)
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        handlePermissionResult(viewModel.consumePermissionRequest(), result.values.all { it })
    }
    companion object {
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
                    settingsViewModel.loadPersistedState()
                    viewModel.loadPersistedData()
                }
            } catch (error: Exception) {
                // 数据层损坏或升级失败不能让 Activity 直接因未处理协程异常闪退；
                // 保留默认内存状态，先让用户进入应用并看到可恢复的提示。
                startupError = error
                Log.e("BarcodeGenerator", "Startup data initialization failed", error)
                DebugLog.record("startup", "data initialization failed", error)
            }
            try {
                // 先应用已保存的外观，再创建动态控件，避免首次进入仍显示浅色页面。
                applyAppearance()
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                buildComposeShell()
                if (state != null && viewModel.uiState.value.page == "generate") {
                    viewModel.navigateTo(state.getString("page", "generate") ?: "generate")
                    viewModel.updateSettingsReturnPage(state.getString("settings_return_page", "generate") ?: "generate")
                    viewModel.setStartupUpdateCheckStarted(state.getBoolean("startup_update_check_started", false))
                }
                if (viewModel.uiState.value.page == "lanShare" && lanShareViewModel.uiState.value.session != null) {
                    lanShareViewModel.uiState.value.session?.let(lanShareViewModel::startAutoRefresh)
                }
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
                if (!viewModel.updateUiState.value.startupCheckStarted) {
                    viewModel.setStartupUpdateCheckStarted(true)
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
        val pendingPath = viewModel.updateUiState.value.pendingInstallPath ?: return
        if (packageManager.canRequestPackageInstalls()) {
            viewModel.takePendingInstallPath()?.let { installApkCompose(File(it)) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", viewModel.uiState.value.page)
        outState.putString("settings_return_page", viewModel.uiState.value.settingsReturnPage)
        outState.putBoolean("startup_update_check_started", viewModel.updateUiState.value.startupCheckStarted)
        super.onSaveInstanceState(outState)
    }

    /** 统一的现代返回回调，保持原有页面返回路径。 */
    private fun handleAppBackPressed() {
        if (viewModel.fireworksVisible.value) {
            dismissFireworksEasterEgg()
            return
        }
        when (viewModel.uiState.value.page) {
            "settings" -> viewModel.navigateTo(viewModel.uiState.value.settingsReturnPage.takeIf { it in setOf("generate", "history", "favorites", "settings") } ?: "generate")
            "betaTestCenter" -> viewModel.navigateTo(AppRoute.Settings)
            "lanShare" -> { closeLanShare(); viewModel.navigateTo(AppRoute.Settings) }
            "favoriteDetail" -> viewModel.navigateTo(AppRoute.Favorites)
            "results" -> viewModel.navigateTo(viewModel.resultUiState.value.returnPage.takeIf { it in setOf("generate", "history", "favorites", "settings") } ?: "generate")
            else -> finish()
        }
    }

    override fun onDestroy() {
        cancelUpdateDownload()
        restoreBarcodeDisplaySettings()
        if (!isChangingConfigurations) {
            closeLanShare()
        }
        super.onDestroy()
    }

    internal fun requestAppPermissions(permissions: Array<String>, requestCode: Int) {
        viewModel.beginPermissionRequest(requestCode)
        permissionLauncher.launch(permissions)
    }

    private fun handlePermissionResult(requestCode: Int, granted: Boolean) {
        if (requestCode == 42) {
            if (granted) {
                when (viewModel.cameraCaptureState.value.requestCode) {
                    REQUEST_LAN_SHARE_CAPTURE -> openLanShareCamera()
                    REQUEST_TEXT_CAMERA -> launchCamera(REQUEST_TEXT_CAMERA)
                    else -> launchCamera(viewModel.cameraCaptureState.value.requestCode)
                }
            } else {
                toast("需要相机权限才能拍照识别")
            }
        } else if (requestCode == REQUEST_LAN_SHARE_GALLERY_PERMISSION) {
            if (granted) openLanShareGalleryPicker() else toast("需要照片权限才能选择图库照片")
        } else if (requestCode == REQUEST_LAN_SHARE_FILE_PERMISSION) {
            if (granted) openLanShareFilePicker() else toast("需要文件权限才能选择文件")
        }
    }

    internal fun launchExternalActivity(intent: Intent, requestCode: Int) {
        viewModel.beginExternalActivityRequest(requestCode)
        externalActivityLauncher.launch(intent)
    }

    private fun handleExternalActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_LAN_SHARE_UPLOAD || requestCode == REQUEST_LAN_SHARE_DOWNLOAD) {
            if (resultCode == RESULT_OK) data?.data?.let { uri ->
                if (requestCode == REQUEST_LAN_SHARE_UPLOAD) selectLanShareAttachment(uri, autoUpload = true)
                else lanShareViewModel.uiState.value.pendingDownloadId?.let { id ->
                    lanShareViewModel.setPendingDownloadId(null)
                    lanShareViewModel.uiState.value.session?.let { session -> lanShareViewModel.downloadFile(session, id, uri) }
                }
            }
            if (resultCode != RESULT_OK && requestCode == REQUEST_LAN_SHARE_DOWNLOAD) lanShareViewModel.setPendingDownloadId(null)
            return
        }
        if (requestCode == REQUEST_LAN_SHARE_CAPTURE) {
            val captureState = viewModel.cameraCaptureState.value
            val captureUri = if (resultCode == RESULT_OK) {
                captureState.outputUri?.takeIf { captureState.outputFile?.length()?.let { size -> size > 0L } == true }
                    ?: findRecentLanCameraMedia()
            } else null
            val captureFile = viewModel.clearCameraOutput().outputFile
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
            viewModel.clearCameraOutput().outputFile?.delete()
            return
        }
        val cameraState = viewModel.cameraCaptureState.value
        val cameraFile = cameraState.outputFile
        val bitmap = when (requestCode) {
            43, 45, 51 -> cameraState.outputUri?.let { uri ->
                runCatching { contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull()
            } ?: (data?.extras?.get("data") as? Bitmap)
            44, 46 -> data?.data?.let { uri ->
                runCatching { contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull()
            }
            else -> null
        }
        if (bitmap == null) {
            viewModel.clearCameraOutput().outputFile?.delete()
            return
        }
        val textBitmap = if (requestCode == 45 || requestCode == 46) prepareTextBitmap(bitmap, cameraFile) else bitmap
        viewModel.clearCameraOutput()
        cameraFile?.delete()
        when (requestCode) {
            43, 44 -> lifecycleScope.launch {
                val decoded = viewModel.decodeBarcode(bitmap)
                // 识别结果直接回填 Compose 生成页，避免依赖已经不再承载界面的旧 EditText。
                if (decoded != null) {
                    viewModel.updateInputDraft(listOf(decoded))
                    toast("条码识别成功")
                } else {
                    toast("未识别到条码，请更换清晰图片")
                }
                bitmap.recycle()
            }
            51 -> lifecycleScope.launch {
                val decoded = viewModel.decodeBarcode(bitmap)
                if (decoded != null) lanShareViewModel.joinSessionFromAddress(decoded) else toast("未识别到分享二维码")
                bitmap.recycle()
            }
            45, 46 -> recognizeText(textBitmap)
        }
    }

}


