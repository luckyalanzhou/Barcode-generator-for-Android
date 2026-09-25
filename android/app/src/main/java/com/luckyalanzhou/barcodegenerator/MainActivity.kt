package com.luckyalanzhou.barcodegenerator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareViewModel
import com.luckyalanzhou.barcodegenerator.presentation.update.UpdateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesViewModel
import com.luckyalanzhou.barcodegenerator.presentation.history.HistoryViewModel
import com.luckyalanzhou.barcodegenerator.presentation.results.ResultsViewModel
import com.luckyalanzhou.barcodegenerator.presentation.navigation.AppNavigationViewModel
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeItemViewModel
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataViewModel
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrViewModel
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import com.luckyalanzhou.barcodegenerator.ui.app.applyAppearance
import com.luckyalanzhou.barcodegenerator.ui.app.buildComposeShell
import com.luckyalanzhou.barcodegenerator.ui.dialogs.cancelUpdateDownload
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.closeLanShare
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.findRecentLanCameraMedia
import com.luckyalanzhou.barcodegenerator.ui.dialogs.installApkCompose
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.openLanShareCamera
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.openLanShareFilePicker
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.openLanShareGalleryPicker
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.selectLanShareAttachment
import com.luckyalanzhou.barcodegenerator.ui.app.showIos26NoticeDialog
import com.luckyalanzhou.barcodegenerator.ui.app.syncSystemBars
import com.luckyalanzhou.barcodegenerator.ui.app.toast
import com.luckyalanzhou.barcodegenerator.ui.dialogs.checkForUpdates
import com.luckyalanzhou.barcodegenerator.ui.dialogs.confirmImportFavorites
import com.luckyalanzhou.barcodegenerator.ui.dialogs.exportFavorites
import com.luckyalanzhou.barcodegenerator.ui.dialogs.launchCamera
import com.luckyalanzhou.barcodegenerator.ui.dialogs.BARCODE_RECOGNITION_MAX_EDGE
import com.luckyalanzhou.barcodegenerator.ui.dialogs.OCR_RECOGNITION_MAX_EDGE
import com.luckyalanzhou.barcodegenerator.ui.dialogs.decodeRecognitionBitmap
import com.luckyalanzhou.barcodegenerator.ui.dialogs.recognizeText
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
import androidx.core.os.BundleCompat
import java.util.concurrent.atomic.AtomicReference
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
        if (navigationViewModel.uiState.value.page != AppRoute.Results) return
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
    internal var pendingResultImageFile: File? = null
    // Tab 选中状态可能在布局刷新时回调；此标志防止回调再次嵌套进入 render。
    internal val navigationViewModel: AppNavigationViewModel by viewModels()
    internal val cameraOcrViewModel: CameraOcrViewModel by viewModels()
    internal val generateViewModel: GenerateViewModel by viewModels()
    internal val settingsViewModel: SettingsViewModel by viewModels()
    internal val lanShareViewModel: LanShareViewModel by viewModels()
    internal val updateViewModel: UpdateViewModel by viewModels()
    internal val favoritesViewModel: FavoritesViewModel by viewModels()
    internal val historyViewModel: HistoryViewModel by viewModels()
    internal val resultsViewModel: ResultsViewModel by viewModels()
    internal val barcodeItemViewModel: BarcodeItemViewModel by viewModels()
    internal val libraryDataViewModel: LibraryDataViewModel by viewModels()

    private val externalActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        handleExternalActivityResult(cameraOcrViewModel.consumeExternalActivityRequest(), result.resultCode, result.data)
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        handlePermissionResult(cameraOcrViewModel.consumePermissionRequest(), result.values.all { it })
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
        const val REQUEST_RESULT_IMAGE_FILE = 58
        const val MAX_HISTORY_ITEMS = 500
        const val MAX_FAVORITE_GROUPS = 200
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        pendingResultImageFile = state?.getString("pending_result_image_file")?.let(::File)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleAppBackPressed()
        })
        DebugLog.initialize(applicationContext)
        DebugLog.record("lifecycle", "onCreate version=${BuildConfig.VERSION_NAME} package=$packageName")
        // 统一由 buildShell 的内边距处理系统栏，避免 Android 15 主题重建时重复 inset 导致页面压缩下移。
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 先挂载 Compose 首屏，再读取设置和迁移旧设置。设置存储或迁移发生等待时，
        // Activity 仍然必须能绘制默认页面，不能停留在窗口背景的黑屏状态。
        if (state != null && navigationViewModel.uiState.value.page == AppRoute.Generate) {
            navigationViewModel.syncNavigationStateFromUi(
                AppRoute.fromPage(state.getString("page", AppRoute.Generate.pageName) ?: AppRoute.Generate.pageName),
            )
            navigationViewModel.updateSettingsReturnPage(
                AppRoute.fromPage(
                    state.getString("settings_return_page", AppRoute.Generate.pageName)
                        ?: AppRoute.Generate.pageName,
                ),
            )
            updateViewModel.setStartupCheckStarted(state.getBoolean("startup_update_check_started", false))
        }
        buildComposeShell()

        lifecycleScope.launch {
            var settingsError: Throwable? = null
            try {
                withContext(Dispatchers.IO) { settingsViewModel.loadPersistedState() }
            } catch (error: Exception) {
                settingsError = error
                Log.e("BarcodeGenerator", "Startup settings initialization failed", error)
                DebugLog.record("startup", "settings initialization failed", error)
            }
            try {
                // 先应用已保存的外观，再创建动态控件，避免首次进入仍显示浅色页面。
                applyAppearance()
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                if (navigationViewModel.uiState.value.page == AppRoute.LanShare && lanShareViewModel.uiState.value.session != null) {
                    lanShareViewModel.uiState.value.session?.let(lanShareViewModel::startAutoRefresh)
                }
            } catch (error: Exception) {
                Log.e("BarcodeGenerator", "Startup UI initialization failed", error)
                DebugLog.record("startup", "UI initialization failed", error)
            }
            if (settingsError != null) {
                window.decorView.post { showStartupFallbackNoticeOnce() }
            }
            // 收藏和历史数据在首帧之后后台加载，避免数据量增长阻塞 Activity 创建和首次绘制。
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { libraryDataViewModel.loadPersistedData() }
                    .onFailure { error ->
                        Log.e("BarcodeGenerator", "Background data initialization failed", error)
                        DebugLog.record("startup", "background data initialization failed", error)
                        withContext(Dispatchers.Main) {
                            showStartupFallbackNoticeOnce()
                        }
                    }
            }
            window.decorView.post {
                if (!updateViewModel.uiState.value.startupCheckStarted) {
                    updateViewModel.setStartupCheckStarted(true)
                    checkForUpdates(silent = true)
                }
            }
        }
    }

    private fun showStartupFallbackNoticeOnce() {
        if (isFinishing || isDestroyed || !libraryDataViewModel.consumeStartupFallbackNotice()) return
        showIos26NoticeDialog("数据加载失败，已使用默认页面启动")
    }

    override fun onPostResume() {
        super.onPostResume()
        // AppCompat 切换浅/深色会重建 Activity；在新窗口完成恢复后再校准一次系统栏。
        if (composeShellReady) syncSystemBars()
    }

    override fun onResume() {
        super.onResume()
        val pendingPath = updateViewModel.uiState.value.pendingInstallPath ?: return
        if (packageManager.canRequestPackageInstalls()) {
            updateViewModel.takePendingInstallPath()?.let { installApkCompose(File(it)) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", navigationViewModel.uiState.value.page.pageName)
        outState.putString("settings_return_page", navigationViewModel.uiState.value.settingsReturnPage.pageName)
        outState.putBoolean("startup_update_check_started", updateViewModel.uiState.value.startupCheckStarted)
        pendingResultImageFile?.let { outState.putString("pending_result_image_file", it.absolutePath) }
        super.onSaveInstanceState(outState)
    }

    /** 统一的现代返回回调，保持原有页面返回路径。 */
    private fun handleAppBackPressed() {
        when (navigationViewModel.uiState.value.page) {
            AppRoute.Settings -> navigationViewModel.navigateTo(navigationViewModel.uiState.value.settingsReturnPage)
            AppRoute.LanShare -> { closeLanShare(); navigationViewModel.navigateTo(AppRoute.Settings) }
            AppRoute.Results -> navigationViewModel.navigateTo(resultsViewModel.resultUiState.value.returnPage)
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
        cameraOcrViewModel.beginPermissionRequest(requestCode)
        permissionLauncher.launch(permissions)
    }

    private fun handlePermissionResult(requestCode: Int, granted: Boolean) {
        if (requestCode == 42) {
            if (granted) {
                when (cameraOcrViewModel.cameraCaptureState.value.requestCode) {
                    REQUEST_LAN_SHARE_CAPTURE -> openLanShareCamera()
                    REQUEST_TEXT_CAMERA -> launchCamera(REQUEST_TEXT_CAMERA)
                    else -> launchCamera(cameraOcrViewModel.cameraCaptureState.value.requestCode)
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
        cameraOcrViewModel.beginExternalActivityRequest(requestCode)
        externalActivityLauncher.launch(intent)
    }

    private fun handleExternalActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_RESULT_IMAGE_FILE) {
            val imageFile = pendingResultImageFile
            pendingResultImageFile = null
            if (resultCode == RESULT_OK && imageFile?.isFile == true && data?.data != null) {
                val written = runCatching {
                    contentResolver.openOutputStream(data.data!!)?.use { output ->
                        imageFile.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("无法打开目标文件")
                }.isSuccess
                if (written) toast("已保存到文件")
                else toast("保存到文件失败")
            }
            imageFile?.delete()
            return
        }
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
            val captureState = cameraOcrViewModel.cameraCaptureState.value
            val captureUri = if (resultCode == RESULT_OK) {
                captureState.outputUri?.takeIf { captureState.outputFile?.length()?.let { size -> size > 0L } == true }
                    ?: findRecentLanCameraMedia()
            } else null
            val captureFile = cameraOcrViewModel.clearCameraOutput().outputFile
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
            cameraOcrViewModel.clearCameraOutput().outputFile?.delete()
            return
        }
        val cameraState = cameraOcrViewModel.cameraCaptureState.value
        val cameraFile = cameraState.outputFile
        val isCameraRequest = requestCode == 43 || requestCode == 45 || requestCode == 51
        val sourceUri = when {
            isCameraRequest -> cameraState.outputUri
            requestCode == 44 || requestCode == 46 -> data?.data
            else -> null
        }
        val maxImageEdge = if (requestCode == 45 || requestCode == 46) {
            OCR_RECOGNITION_MAX_EDGE
        } else {
            BARCODE_RECOGNITION_MAX_EDGE
        }
        cameraOcrViewModel.clearCameraOutput()
        val cameraThumbnail = if (isCameraRequest) {
            data?.extras?.let { BundleCompat.getParcelable(it, "data", Bitmap::class.java) }
        } else {
            null
        }
        val pendingBitmap = AtomicReference<Bitmap?>(cameraThumbnail)
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val decodedBitmap = sourceUri?.let { decodeRecognitionBitmap(it, maxImageEdge) }
                    val selectedBitmap = decodedBitmap ?: cameraThumbnail
                    if (decodedBitmap != null && cameraThumbnail != null && decodedBitmap !== cameraThumbnail) {
                        pendingBitmap.compareAndSet(cameraThumbnail, null)
                        if (!cameraThumbnail.isRecycled) cameraThumbnail.recycle()
                    }
                    selectedBitmap?.also(pendingBitmap::set)
                } ?: return@launch
                when (requestCode) {
                    43, 44 -> {
                        val decoded = try {
                            cameraOcrViewModel.decodeBarcode(bitmap)
                        } finally {
                            pendingBitmap.compareAndSet(bitmap, null)
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                        // 识别结果直接回填 Compose 生成页，避免依赖已经不再承载界面的旧 EditText。
                        if (decoded != null) {
                            generateViewModel.updateDraft(listOf(decoded))
                            toast("条码识别成功")
                        } else {
                            toast("未识别到条码，请更换清晰图片")
                        }
                    }
                    51 -> {
                        val decoded = try {
                            cameraOcrViewModel.decodeBarcode(bitmap)
                        } finally {
                            pendingBitmap.compareAndSet(bitmap, null)
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                        if (decoded != null) lanShareViewModel.joinSessionFromAddress(decoded) else toast("未识别到分享二维码")
                    }
                    45, 46 -> {
                        pendingBitmap.compareAndSet(bitmap, null)
                        recognizeText(bitmap)
                    }
                }
            } finally {
                pendingBitmap.getAndSet(null)?.let { if (!it.isRecycled) it.recycle() }
                cameraFile?.delete()
            }
        }
    }

}


