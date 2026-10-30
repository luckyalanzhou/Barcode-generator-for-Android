package com.luckyalanzhou.barcodegenerator

import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.luckyalanzhou.barcodegenerator.ui.feature.results.ResultExportAction
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
import com.luckyalanzhou.barcodegenerator.ui.app.GenerateEditorInstanceState
import com.luckyalanzhou.barcodegenerator.ui.app.GenerateEditorDraftAutosaver
import com.luckyalanzhou.barcodegenerator.ui.app.GenerateEditorDraftStore
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareViewModel
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareQrBitmapFactory
import com.luckyalanzhou.barcodegenerator.presentation.update.UpdateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesViewModel
import com.luckyalanzhou.barcodegenerator.presentation.history.HistoryViewModel
import com.luckyalanzhou.barcodegenerator.presentation.results.ResultsViewModel
import com.luckyalanzhou.barcodegenerator.presentation.navigation.AppNavigationViewModel
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeItemViewModel
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataViewModel
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrViewModel
import com.luckyalanzhou.barcodegenerator.data.platform.cleanupExpiredApkDownloadFiles
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import com.luckyalanzhou.barcodegenerator.ui.app.applyAppearance
import com.luckyalanzhou.barcodegenerator.ui.app.buildComposeShell
import com.luckyalanzhou.barcodegenerator.ui.app.finishPendingResultImageDocument
import com.luckyalanzhou.barcodegenerator.ui.app.restorePendingResultExportAction
import com.luckyalanzhou.barcodegenerator.ui.app.platform.cancelUpdateDownload
import com.luckyalanzhou.barcodegenerator.ui.app.platform.runDocumentProviderCleanupResult
import com.luckyalanzhou.barcodegenerator.ui.app.platform.cleanupInterruptedCameraCaptureFiles
import com.luckyalanzhou.barcodegenerator.ui.app.platform.shareableCacheDirectory
import com.luckyalanzhou.barcodegenerator.ui.app.platform.SHAREABLE_CAMERA_DIRECTORY
import com.luckyalanzhou.barcodegenerator.ui.app.lanshare.closeLanShare
import com.luckyalanzhou.barcodegenerator.ui.app.lanshare.findRecentLanCameraMedia
import com.luckyalanzhou.barcodegenerator.ui.app.lanshare.resolveLanCameraCaptureTarget
import com.luckyalanzhou.barcodegenerator.ui.app.platform.installApkCompose
import com.luckyalanzhou.barcodegenerator.ui.app.platform.StartupFailureNoticeCoordinator
import com.luckyalanzhou.barcodegenerator.ui.app.lanshare.openLanShareCamera
import com.luckyalanzhou.barcodegenerator.ui.app.lanshare.openLanShareFilePicker
import com.luckyalanzhou.barcodegenerator.ui.app.lanshare.openLanShareGalleryPicker
import com.luckyalanzhou.barcodegenerator.ui.app.lanshare.selectLanShareAttachment
import com.luckyalanzhou.barcodegenerator.ui.app.showIos26NoticeDialog
import com.luckyalanzhou.barcodegenerator.ui.app.syncSystemBars
import com.luckyalanzhou.barcodegenerator.ui.app.toast
import com.luckyalanzhou.barcodegenerator.ui.app.platform.confirmImportFavorites
import com.luckyalanzhou.barcodegenerator.ui.app.platform.exportFavorites
import com.luckyalanzhou.barcodegenerator.ui.app.platform.launchCamera
import com.luckyalanzhou.barcodegenerator.ui.dialogs.BARCODE_RECOGNITION_MAX_EDGE
import com.luckyalanzhou.barcodegenerator.ui.dialogs.OCR_RECOGNITION_MAX_EDGE
import com.luckyalanzhou.barcodegenerator.ui.app.platform.decodeRecognitionBitmap
import com.luckyalanzhou.barcodegenerator.ui.app.platform.recognizeText
import android.util.Log
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import java.util.concurrent.atomic.AtomicReference
import androidx.core.view.WindowCompat
import androidx.appcompat.app.AppCompatActivity
import java.io.File

private const val CAMERA_CAPTURE_CLEANUP_GRACE_MILLIS = 5_000L

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var lanShareQrBitmapFactory: LanShareQrBitmapFactory
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
    /** 权限提示窗口只在当前 Activity 生命周期内去重；恢复后根据待安装路径重新展示。 */
    internal var installPermissionDialogShowing: Boolean = false
    internal var pendingResultImageFile: File? = null
    /** 外部相机回传后仍在解码或上传的临时文件，避免前台恢复清理与异步处理竞争。 */
    private val activeCameraCapturePaths = mutableSetOf<String>()
    /** 生成页大草稿的实例级文件 ID，随 Activity saved state 跨重建恢复。 */
    private var generateDraftId: String? = null
    /** 大草稿异步恢复期间保留原始引用，避免 Activity 再次保存时写入空草稿。 */
    private var pendingGenerateDraftState: Bundle? = null
    /** 生成页大草稿在编辑期间的后台预存器；状态保存仅引用已完成的快照。 */
    private var generateDraftAutosaver: GenerateEditorDraftAutosaver? = null
    internal var resultExportAction by mutableStateOf<ResultExportAction?>(null)
    internal var resultImageContentWidthPx: Int = 0
    internal val preparingResultExport: Boolean get() = resultExportAction != null
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
        val permissionResult = cameraOcrViewModel.consumePermissionRequest(result)
        handlePermissionResult(permissionResult.requestCode, permissionResult.granted)
    }
    companion object {
        const val REQUEST_CAMERA_PERMISSION = 42
        const val REQUEST_TEXT_CAMERA = 45
         const val REQUEST_FAVORITES_EXPORT = 49
         const val REQUEST_FAVORITES_IMPORT = 50
        const val REQUEST_LAN_SHARE_UPLOAD = 52
         const val REQUEST_LAN_SHARE_DOWNLOAD = 53
        const val REQUEST_LAN_SHARE_CAPTURE = 54
        const val REQUEST_LAN_SHARE_GALLERY_PERMISSION = 56
        const val REQUEST_LAN_SHARE_FILE_PERMISSION = 57
        const val REQUEST_RESULT_IMAGE_FILE = 58
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        pendingResultImageFile = state?.getString("pending_result_image_file")?.let(::File)
        restorePendingResultExportAction(pendingResultImageFile != null) { resultExportAction = it }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleAppBackPressed()
        })
        DebugLog.initialize(applicationContext)
        DebugLog.installUncaughtExceptionHandler()
        DebugLog.record(
            "lifecycle",
            "onCreate version=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE} pid=${android.os.Process.myPid()} package=$packageName",
        )
        // 统一由 buildShell 的内边距处理系统栏，避免 Android 15 主题重建时重复 inset 导致页面压缩下移。
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 常规启动与小草稿恢复后立即挂载 Compose；大草稿先在后台读取，完成后再挂载，
        // 避免主线程解析文件，也避免空状态 autosaver 覆盖尚未恢复的草稿。
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
        }
        if (state?.getBoolean(GenerateEditorInstanceState.FILE_SAVED_KEY) == true) {
            // 私有文件可能包含数千行输入；等后台恢复完成后再挂载 Compose，避免首帧被文件读取阻塞。
            pendingGenerateDraftState = Bundle(state)
            lifecycleScope.launch {
                val restoredDraft = GenerateEditorInstanceState.restoreLargeDraft(
                    applicationContext,
                    pendingGenerateDraftState,
                )
                restoredDraft?.let { draft ->
                    generateViewModel.restoreSavedDraft(draft.inputs, draft.formatName)
                    generateDraftId = draft.draftId
                }
                pendingGenerateDraftState = null
                initializeComposeShell()
            }
        } else {
            GenerateEditorInstanceState.restore(applicationContext, state, generateViewModel::restoreSavedDraft) {
                generateDraftId = it
            }
            initializeComposeShell()
        }
    }

    /** 草稿恢复完成后挂载首屏，并启动设置、数据与更新检查。 */
    private fun initializeComposeShell() {
        lifecycleScope.launch(Dispatchers.IO) {
            GenerateEditorDraftStore(applicationContext.noBackupFilesDir).cleanupExpired()
        }
        generateDraftAutosaver = GenerateEditorDraftAutosaver(applicationContext.noBackupFilesDir).also {
            it.observe(lifecycleScope, generateViewModel.uiState)
        }
        buildComposeShell()

        // ViewModelLazy/Hilt ViewModel 的创建和清理必须在主线程执行。
        // 启动任务派发到后台前，先在此处取得这些 Activity 作用域实例。
        val startupSettingsViewModel = settingsViewModel
        val startupLibraryDataViewModel = libraryDataViewModel
        val startupFailureNotices = StartupFailureNoticeCoordinator()

        lifecycleScope.launch {
            var settingsError: Throwable? = null
            try {
                startupSettingsViewModel.loadPersistedState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                settingsError = error
                Log.e("BarcodeGenerator", "Startup settings initialization failed", error)
                DebugLog.record("startup", "settings initialization failed", error)
            }
            try {
                // 先应用已保存的外观，再创建动态控件，避免首次进入仍显示浅色页面。
                applyAppearance()
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            } catch (error: Exception) {
                Log.e("BarcodeGenerator", "Startup UI initialization failed", error)
                DebugLog.record("startup", "UI initialization failed", error)
            }
            startupFailureNotices.reportSettingsLoad(failed = settingsError != null)?.let {
                if (!isFinishing && !isDestroyed) showIos26NoticeDialog(it)
            }
            // 收藏和历史数据在首帧之后后台加载，避免数据量增长阻塞 Activity 创建和首次绘制。
            lifecycleScope.launch(Dispatchers.IO) {
                var libraryLoadFailed = false
                try {
                    startupLibraryDataViewModel.loadPersistedData()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    libraryLoadFailed = true
                    Log.e("BarcodeGenerator", "Background data initialization failed", error)
                    DebugLog.record("startup", "background data initialization failed", error)
                }
                val fallbackNotice = startupFailureNotices.reportLibraryLoad(failed = libraryLoadFailed)
                if (fallbackNotice != null) withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) showIos26NoticeDialog(fallbackNotice)
                }
            }
            window.decorView.post {
                updateViewModel.startStartupCheck()
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
        cleanupInterruptedCameraCaptures()
        cleanupExpiredUpdateApkFiles()
        val pendingInstallPath = updateViewModel.uiState.value.pendingInstallPath ?: return
        if (packageManager.canRequestPackageInstalls()) {
            updateViewModel.takePendingInstallPath()?.let {
                installPermissionDialogShowing = false
                installApkCompose(File(it))
            }
        } else if (!installPermissionDialogShowing) {
            installApkCompose(File(pendingInstallPath))
        }
    }

    /** Activity 恢复后清理过期更新包，并保留 SavedState 中等待安装的 APK。 */
    private fun cleanupExpiredUpdateApkFiles() {
        val protectedApkPaths = updateViewModel.uiState.value.pendingInstallPath
            ?.let(::setOf)
            .orEmpty()
        val cacheDirectory = cacheDir
        val nowMillis = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            val removedCount = cleanupExpiredApkDownloadFiles(cacheDirectory, protectedApkPaths, nowMillis)
            if (removedCount > 0) {
                DebugLog.record("update", "removed expired APK cache files count=$removedCount")
            }
        }
    }

    /** Activity 已恢复 SavedStateHandle 后再清理，保留仍等待外部相机回传的输出文件。 */
    private fun cleanupInterruptedCameraCaptures() {
        val protectedPaths = buildSet {
            cameraOcrViewModel.cameraCaptureState.value.outputFile?.absolutePath?.let(::add)
            lanShareViewModel.uiState.value.pendingUploadTempFile?.absolutePath?.let(::add)
            addAll(activeCameraCapturePaths)
        }
        val cacheDirectory = cacheDir
        val modifiedBeforeMillis = System.currentTimeMillis() - CAMERA_CAPTURE_CLEANUP_GRACE_MILLIS
        lifecycleScope.launch(Dispatchers.IO) {
            cleanupInterruptedCameraCaptureFiles(cacheDirectory, modifiedBeforeMillis, protectedPaths)
            cleanupInterruptedCameraCaptureFiles(
                shareableCacheDirectory(cacheDirectory, SHAREABLE_CAMERA_DIRECTORY),
                modifiedBeforeMillis,
                protectedPaths,
            )
        }
    }

    private fun retainCameraCapture(file: File?) {
        file?.absolutePath?.let(activeCameraCapturePaths::add)
    }

    private fun releaseCameraCapture(file: File?) {
        file?.absolutePath?.let(activeCameraCapturePaths::remove)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", navigationViewModel.uiState.value.page.pageName)
        outState.putString("settings_return_page", navigationViewModel.uiState.value.settingsReturnPage.pageName)
        val pendingDraftState = pendingGenerateDraftState
        val savedGenerateDraft = if (pendingDraftState != null) {
            GenerateEditorInstanceState.copyPendingDraftState(pendingDraftState, outState)
            true
        } else {
            val generateState = generateViewModel.uiState.value
            val autosaver = generateDraftAutosaver
            val prePersistedDraft = autosaver?.snapshotFor(generateState)
            GenerateEditorInstanceState.save(
                applicationContext,
                generateState,
                outState,
                generateDraftId,
                prePersistedDraft,
                autosaver?.takeIf { prePersistedDraft == null }?.let { autosaver ->
                    generateDraftId?.takeIf(autosaver::ownsDraftId)
                },
            ).also {
                generateDraftId = outState.getString(GenerateEditorInstanceState.FILE_ID_KEY)
            }
        }
        if (!savedGenerateDraft) {
            val generateState = generateViewModel.uiState.value
            DebugLog.record(
                "lifecycle",
                "generate draft omitted from instance state rows=${generateState.inputDraft.size} reason=size_or_storage_limit",
            )
        }
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
        DebugLog.record(
            "lifecycle",
            "onDestroy finishing=$isFinishing changingConfigurations=$isChangingConfigurations pid=${android.os.Process.myPid()}",
        )
        cancelUpdateDownloadIfActivityFinishes(isChangingConfigurations, ::cancelUpdateDownload)
        restoreBarcodeDisplaySettings()
        if (!isChangingConfigurations) {
            closeLanShare()
        }
        super.onDestroy()
    }

    internal fun requestAppPermissions(permissions: Array<String>, requestCode: Int, cameraRequestCode: Int? = null) {
        if (!cameraOcrViewModel.beginPermissionRequest(requestCode, permissions, cameraRequestCode)) {
            DebugLog.record("permission", "launch_rejected requestCode=$requestCode reason=request_pending")
            toast("权限请求正在处理中，请稍后重试")
            return
        }
        launchPermissionRequestSafely(
            launch = { permissionLauncher.launch(permissions) },
            onFailure = { error ->
                cameraOcrViewModel.cancelPermissionRequest()
                DebugLog.record(
                    "permission",
                    "launch_failed requestCode=$requestCode exception=${error.javaClass.simpleName}",
                )
                toast("无法打开权限请求，请重试")
            },
        )
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

    internal fun launchExternalActivity(intent: Intent, requestCode: Int): Boolean {
        if (!cameraOcrViewModel.beginExternalActivityRequest(requestCode)) {
            DebugLog.record("external_activity", "launch_rejected requestCode=$requestCode reason=request_pending")
            toast("已有系统操作正在进行，请完成后重试")
            return false
        }
        return try {
            externalActivityLauncher.launch(intent)
            true
        } catch (error: Exception) {
            DebugLog.record(
                "external_activity",
                "launch_failed requestCode=$requestCode exception=${error.javaClass.simpleName}",
            )
            cameraOcrViewModel.consumeExternalActivityRequest()
            toast("无法打开系统选择器")
            false
        }
    }

    /** 在调用方写入相机或下载临时状态前，避免拒绝新请求时破坏旧请求的数据。 */
    internal fun canStartExternalActivity(): Boolean {
        if (!cameraOcrViewModel.hasPendingExternalActivityRequest()) return true
        DebugLog.record("external_activity", "launch_rejected reason=request_pending")
        toast("已有系统操作正在进行，请完成后重试")
        return false
    }

    private fun handleExternalActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_RESULT_IMAGE_FILE) {
            val imageFile = pendingResultImageFile
            pendingResultImageFile = null
            if (resultCode == RESULT_OK) {
                if (imageFile == null || !imageFile.isFile) {
                    DebugLog.record("results", "result_image_save_source_missing")
                    toast("待保存图片已失效，请重新生成后重试")
                } else {
                    val targetUri = data?.data
                    if (targetUri == null) {
                        DebugLog.record("results", "result_image_save_returned_without_uri")
                        toast("未能获取保存位置，请重试")
                    } else {
                        lifecycleScope.launch {
                            val operation = System.nanoTime()
                            DebugLog.record("results", "result_image_save_started operation=$operation")
                            try {
                                val writeErrorClass = withContext(Dispatchers.IO) {
                                    try {
                                        com.luckyalanzhou.barcodegenerator.ui.app.platform.writeCreatedDocumentWithRollback(
                                            write = {
                                                contentResolver.openOutputStream(targetUri)?.use { output ->
                                                    imageFile.inputStream().use { input -> input.copyTo(output) }
                                                } ?: error("无法打开目标文件")
                                            },
                                            deleteCreatedDocument = {
                                                android.provider.DocumentsContract.deleteDocument(contentResolver, targetUri)
                                            },
                                        )
                                        null
                                    } catch (cancel: CancellationException) {
                                        throw cancel
                                    } catch (error: Exception) {
                                        val cleanup = error.suppressed.firstOrNull()
                                            ?.let { "+cleanup_${it.javaClass.simpleName}" }
                                            .orEmpty()
                                        "${error.javaClass.simpleName}$cleanup"
                                    }
                                }
                                val written = writeErrorClass == null
                                if (writeErrorClass != null) {
                                    DebugLog.record(
                                        "results",
                                        "result_image_save_failed operation=$operation exception=$writeErrorClass",
                                    )
                                } else {
                                    DebugLog.record("results", "result_image_save_completed operation=$operation")
                                }
                                toast(if (written) "已保存到文件" else "保存到文件失败")
                            } catch (cancelled: CancellationException) {
                                DebugLog.record("results", "result_image_save_cancelled operation=$operation")
                                throw cancelled
                            } finally {
                                finishPendingResultImageDocument(imageFile) { resultExportAction = null }
                            }
                        }
                        return
                    }
                }
            }
            finishPendingResultImageDocument(imageFile) { resultExportAction = null }
            return
        }
        if (requestCode == REQUEST_LAN_SHARE_UPLOAD || requestCode == REQUEST_LAN_SHARE_DOWNLOAD) {
            if (requestCode == REQUEST_LAN_SHARE_UPLOAD) {
                if (resultCode == RESULT_OK) {
                    val uri = data?.data
                    if (uri != null) lifecycleScope.launch {
                        selectLanShareAttachment(uri, autoUpload = true)
                    }
                    else {
                        DebugLog.record("lan_share", "upload_picker_returned_without_uri")
                        toast("未能读取所选文件，请重新选择")
                    }
                }
                return
            }

            val downloadId = lanShareViewModel.uiState.value.pendingDownloadId
            lanShareViewModel.setPendingDownloadId(null)
            if (resultCode == RESULT_OK) {
                val uri = data?.data
                if (uri == null) {
                    DebugLog.record("lan_share", "download_picker_returned_without_uri")
                    toast("未能获取保存位置，请重试")
                    return
                }
                val session = lanShareViewModel.uiState.value.session
                if (downloadId == null || session == null) {
                    DebugLog.record("lan_share", "download_picker_returned_without_active_transfer")
                    toast("分享连接已结束，请重新连接后重试")
                    lifecycleScope.launch {
                        val cleanup = runDocumentProviderCleanupResult { contentResolver.delete(uri, null, null) }
                        if ((cleanup.getOrNull() ?: 0) <= 0) {
                            val failureType = cleanup.exceptionOrNull()?.javaClass?.simpleName ?: "no_document_deleted"
                            DebugLog.record("lan_share", "download target cleanup not confirmed type=$failureType")
                        }
                    }
                    return
                }
                lanShareViewModel.downloadFile(session, downloadId, uri)
            }
            return
        }
        if (requestCode == REQUEST_LAN_SHARE_CAPTURE) {
            val captureState = cameraOcrViewModel.cameraCaptureState.value
            val captureFile = cameraOcrViewModel.clearCameraOutput().outputFile
            if (resultCode != RESULT_OK) {
                captureFile?.delete()
                return
            }
            retainCameraCapture(captureFile)
            lifecycleScope.launch {
                try {
                    val captureUri = withContext(Dispatchers.IO) {
                        resolveLanCameraCaptureTarget(
                            output = captureState.outputUri,
                            outputHasContent = captureFile?.length()?.let { it > 0L } == true,
                        ) { findRecentLanCameraMedia(captureState.startedAtMillis) }
                    }
                    if (captureUri != null) {
                        selectLanShareAttachment(captureUri, captureFile, autoUpload = true)
                    } else {
                        captureFile?.delete()
                    }
                } catch (cancelled: CancellationException) {
                    captureFile?.delete()
                    throw cancelled
                } catch (error: Exception) {
                    captureFile?.delete()
                    DebugLog.record("lan_share", "camera_media_lookup_failed", error)
                    toast("无法读取刚拍摄的照片")
                } finally {
                    releaseCameraCapture(captureFile)
                }
            }
            return
        }
        if (requestCode == REQUEST_FAVORITES_EXPORT || requestCode == REQUEST_FAVORITES_IMPORT) {
            if (resultCode == RESULT_OK) {
                val uri = data?.data
                if (uri == null) {
                    DebugLog.record("favorites", "backup_picker_returned_without_uri request=$requestCode")
                    toast("未能获取备份文件，请重试")
                } else {
                    when (requestCode) {
                        REQUEST_FAVORITES_EXPORT -> exportFavorites(uri)
                        REQUEST_FAVORITES_IMPORT -> confirmImportFavorites(uri)
                    }
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
        retainCameraCapture(cameraFile)
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
                } ?: run {
                    DebugLog.record("image_decode", "recognition bitmap unavailable requestCode=$requestCode")
                    toast("无法读取所选图片，请选择其他图片后重试")
                    return@launch
                }
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
                releaseCameraCapture(cameraFile)
            }
        }
    }

}


