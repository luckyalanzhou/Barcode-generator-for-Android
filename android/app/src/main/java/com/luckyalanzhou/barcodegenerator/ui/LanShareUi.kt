package com.luckyalanzhou.barcodegenerator

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.graphics.drawable.GradientDrawable
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 局域网分享的业务和系统桥接。
 *
 * 可见页面、文件气泡、附件菜单和二维码弹窗由 ComposeLanShareUi.kt 负责；
 * 本文件保留局域网会话、轮询、文件传输、系统相机/图库/文件选择器。
 */
internal fun MainActivity.enterLanShare() {
    if (!lanShareManager.isOnLocalNetwork()) {
        showLanShareNetworkErrorDialog()
        return
    }
    settingsReturnPage = "settings"
    page = "lanShare"
    runCatching {
        lanShareSession = lanShareManager.start()
        lanShareIsHost = true
        lanShareQrVisible = true
        lanShareBrowserConnected = false
        lanShareOwnFileIds.clear()
        lanShareFiles = lanShareManager.localFiles()
        startLanShareAutoRefresh()
        render()
    }.onFailure {
        stopLanShareAutoRefresh()
        lanShareSession = null
        lanShareIsHost = false
        page = "settings"
        render()
        if (it.message == "Error 当前不处于局域网") showLanShareNetworkErrorDialog()
        else toast(it.message ?: "无法创建房间")
    }
}

internal fun MainActivity.showLanShareNetworkErrorDialog(showMetrics: Boolean = false) {
    showIos26NoticeDialogCompose("Error: 当前不处于局域网", showMetrics)
}

/** 兼容旧导航入口，界面由 ComposeAppShell 路由。 */
internal fun MainActivity.showLanShare() {
    page = "lanShare"
    composeShellRevision.intValue++
}

internal fun MainActivity.openLanShareCamera() {
    pendingCameraRequest = MainActivity.REQUEST_LAN_SHARE_CAPTURE
    if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), MainActivity.REQUEST_CAMERA_PERMISSION)
        return
    }
    val photoFile = File.createTempFile("lan_share_photo_", ".jpg", cacheDir)
    val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
    pendingCameraUri = photoUri
    pendingCameraFile = photoFile
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("output", photoUri)
    }
    try {
        startActivityForResult(intent, MainActivity.REQUEST_LAN_SHARE_CAPTURE)
    } catch (_: Exception) {
        pendingCameraUri = null
        pendingCameraFile = null
        photoFile.delete()
        toast("当前设备没有可用的系统相机")
    }
}

internal fun MainActivity.findRecentLanCameraMedia(): Uri? {
    val threshold = (pendingLanCameraStartedAt - 2_000L).coerceAtLeast(0L) / 1_000L
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    return contentResolver.query(
        collection,
        arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DATE_ADDED),
        null,
        null,
        "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC",
    )?.use { cursor ->
        if (cursor.moveToFirst() && cursor.getLong(1) >= threshold) {
            android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
        } else null
    }
}

internal fun MainActivity.openLanShareGallery() {
    if (Build.VERSION.SDK_INT >= 33) {
        openLanShareGalleryPicker()
        return
    }
    val permission = Manifest.permission.READ_EXTERNAL_STORAGE
    if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(permission), MainActivity.REQUEST_LAN_SHARE_GALLERY_PERMISSION)
    } else openLanShareGalleryPicker()
}

internal fun MainActivity.openLanShareGalleryPicker() {
    val intent = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES)
    else Intent(Intent.ACTION_PICK).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
    startActivityForResult(
        intent.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },
        MainActivity.REQUEST_LAN_SHARE_UPLOAD,
    )
}

internal fun MainActivity.openLanShareFiles() {
    if (Build.VERSION.SDK_INT <= 32 &&
        checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), MainActivity.REQUEST_LAN_SHARE_FILE_PERMISSION)
    } else openLanShareFilePicker()
}

internal fun MainActivity.openLanShareFilePicker() {
    startActivityForResult(
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        },
        MainActivity.REQUEST_LAN_SHARE_UPLOAD,
    )
}

internal fun MainActivity.selectLanShareAttachment(uri: Uri, temporaryFile: File? = null, autoUpload: Boolean = false) {
    pendingLanUploadTempFile?.takeIf { it != temporaryFile }?.delete()
    pendingLanUploadUri = uri
    pendingLanUploadTempFile = temporaryFile
    pendingLanUploadName = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }.getOrNull() ?: "附件"
    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    if (autoUpload) uploadSelectedLanShareFile()
    else {
        composeLanShareRevision.intValue++
        toast("已选择附件，点击发送按钮发送")
    }
}

internal fun MainActivity.uploadSelectedLanShareFile() {
    val uri = pendingLanUploadUri ?: return
    val temporaryFile = pendingLanUploadTempFile
    pendingLanUploadUri = null
    pendingLanUploadTempFile = null
    pendingLanUploadName = null
    uploadLanShareFile(uri, temporaryFile)
}

internal fun MainActivity.joinLanShareSession(value: String) {
    val address = value.trim()
    if (!address.startsWith("http://", ignoreCase = true)) {
        toast("这不是局域网分享地址")
        return
    }
    val uri = Uri.parse(address)
    if (uri.host.isNullOrBlank() || uri.port !in 1..65535 || uri.query != null ||
        uri.fragment != null || uri.userInfo != null || !lanShareManager.isRouterLanHost(uri.host)
    ) {
        toast("这不是局域网分享地址")
        return
    }
    stopLanShareAutoRefresh()
    lanShareManager.stop()
    lanShareIsHost = false
    lanShareSession = LanShareSession("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else 80}")
    startLanShareAutoRefresh()
    refreshLanShareFiles()
}

internal fun MainActivity.showLanShareQrDialog(simulatedSession: LanShareSession? = null) {
    showLanShareQrDialogCompose(simulatedSession)
}

internal fun MainActivity.startLanShareAutoRefresh() {
    stopLanShareAutoRefresh()
    val task = object : Runnable {
        override fun run() {
            if (page != "lanShare" || lanShareSession == null) return
            refreshLanShareFiles(showError = false)
            lanShareRefreshHandler.postDelayed(this, 1_500L)
        }
    }
    lanShareRefreshRunnable = task
    lanShareRefreshHandler.postDelayed(task, 1_500L)
}

internal fun MainActivity.stopLanShareAutoRefresh() {
    lanShareRefreshRunnable?.let(lanShareRefreshHandler::removeCallbacks)
    lanShareRefreshRunnable = null
}

internal fun MainActivity.refreshLanShareFiles(showError: Boolean = true) {
    val session = lanShareSession ?: return
    if (lanShareRefreshInFlight) return
    lanShareRefreshInFlight = true
    lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching { lanShareManager.list(session) }
        runOnUiThread {
            lanShareRefreshInFlight = false
            lanShareBrowserConnected = lanShareManager.browserConnected()
            result.onSuccess { files ->
                lanShareFiles = files
                val imageIds = files.filter { isLanShareImageName(it.name) }.map { it.id }.toSet()
                lanSharePreviewFiles.keys.retainAll(imageIds)
                if (page == "lanShare") composeLanShareRevision.intValue++
                if (lanSharePreviewJob?.isActive != true) {
                    lanSharePreviewJob = lifecycleScope.launch(Dispatchers.IO) {
                        val previews = fetchLanSharePreviews(session, files)
                        withContext(Dispatchers.Main) {
                            if (lanShareSession == session && page == "lanShare") {
                                val currentIds = lanShareFiles.map { it.id }.toSet()
                                lanSharePreviewFiles.putAll(previews.filterKeys { it in currentIds })
                                composeLanShareRevision.intValue++
                            }
                        }
                    }
                }
            }
            result.onFailure { if (showError) toast("无法连接到分享房间") }
        }
    }
}

internal fun MainActivity.closeLanShare() {
    stopLanShareAutoRefresh()
    lanSharePreviewJob?.cancel()
    lanSharePreviewJob = null
    lanShareManager.stop(clearSharedFiles = true)
    lanShareSession = null
    lanShareFiles = emptyList()
    lanShareOwnFileIds.clear()
    lanSharePreviewFiles.clear()
    composeLanShareClearInput = null
    File(cacheDir, "lan-share-preview").listFiles().orEmpty().forEach { it.delete() }
}

private suspend fun MainActivity.fetchLanSharePreviews(session: LanShareSession, files: List<LanShareFile>): Map<String, File> {
    val previewFolder = File(cacheDir, "lan-share-preview").apply { mkdirs() }
    val imageIds = files.filter { isLanShareImageName(it.name) }.map { it.id }.toSet()
    previewFolder.listFiles().orEmpty().filter { it.name !in imageIds }.forEach { it.delete() }
    var cachedBytes = previewFolder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
    return buildMap {
        files.filter { isLanShareImageName(it.name) && lanShareManager.localFile(it.id) == null }.forEach { file ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val preview = File(previewFolder, file.id)
            if (!preview.isFile && file.size <= 16L * 1024L * 1024L &&
                cachedBytes + file.size <= 64L * 1024L * 1024L
            ) {
                runCatching { lanShareManager.downloadPreview(session, file.id, preview) }
                cachedBytes += preview.length()
            }
            if (preview.isFile) put(file.id, preview)
        }
    }
}

internal fun MainActivity.uploadLanShareFile(uri: Uri, temporaryFile: File? = null) {
    val session = lanShareSession ?: return
    lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            val id = lanShareManager.upload(session, uri)
            id to lanShareManager.list(session)
        }.onSuccess { (id, files) ->
            temporaryFile?.delete()
            runOnUiThread {
                lanShareOwnFileIds.add(id)
                lanShareFiles = files
                composeLanShareRevision.intValue++
            }
        }.onFailure {
            temporaryFile?.delete()
            runOnUiThread { toast("上传失败") }
        }
    }
}

internal fun MainActivity.uploadLanShareMessage(text: String) {
    val session = lanShareSession ?: return
    lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            val id = lanShareManager.uploadText(session, text)
            id to lanShareManager.list(session)
        }.onSuccess { (id, files) ->
            runOnUiThread {
                composeLanShareClearInput?.invoke()
                lanShareOwnFileIds.add(id)
                lanShareFiles = files
                composeLanShareRevision.intValue++
                toast("发送成功")
            }
        }.onFailure { runOnUiThread { toast("发送失败") } }
    }
}

internal fun MainActivity.downloadLanShareFile(id: String, uri: Uri) {
    val session = lanShareSession ?: return
    lifecycleScope.launch(Dispatchers.IO) {
        runCatching { lanShareManager.download(session, id, uri) }
            .onSuccess { runOnUiThread { toast("下载完成") } }
            .onFailure { runOnUiThread { toast("下载失败") } }
    }
}

internal fun MainActivity.saveLanShareFile(file: LanShareFile) {
    val mime = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase())
        ?: "application/octet-stream"
    pendingLanDownloadId = file.id
    startActivityForResult(
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mime
            putExtra(Intent.EXTRA_TITLE, file.name)
            addCategory(Intent.CATEGORY_OPENABLE)
        },
        MainActivity.REQUEST_LAN_SHARE_DOWNLOAD,
    )
}

internal fun isLanShareImageName(name: String) =
    name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif")

/** 相机照片常把方向保存在 EXIF；BitmapFactory 不会自动应用，预览前校正方向。 */
internal fun decodeLanSharePreview(file: File): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
        else -> 0f
    }
    if (rotation == 0f) return bitmap
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
    }.getOrDefault(bitmap)
}

internal fun formatLanShareSize(bytes: Long): String =
    if (bytes >= 1024L * 1024L) {
        String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
    } else {
        "${bytes / 1024} KB"
    }
