package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.showIos26NoticeDialogCompose

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile

import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.graphics.drawable.GradientDrawable
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Locale

/**
 * 局域网分享的业务和系统桥接。
 *
 * 可见页面、文件气泡、附件菜单和二维码弹窗由 LanShareContent、LanShareMessageBubble、
 * LanShareInputBar 和 ComposeLanShareQrDialog 负责；
 * 本文件保留局域网会话、轮询、文件传输、系统相机/图库/文件选择器。
 */
internal fun MainActivity.enterLanShare() {
    if (!lanShareViewModel.isOnLocalNetwork()) {
        showLanShareNetworkErrorDialog()
        return
    }
    viewModel.updateSettingsReturnPage(AppRoute.Settings)
    composeAppShellActions().navigateTo(AppRoute.LanShare)
    runCatching {
        lanShareViewModel.startHostSession()
        lanShareViewModel.uiState.value.session?.let(lanShareViewModel::startAutoRefresh)
    }.onFailure {
        lanShareViewModel.stopAutoRefresh()
        lanShareViewModel.closeSession()
        composeAppShellActions().navigateTo(AppRoute.Settings)
        if (it.message == "Error 当前不处于局域网") showLanShareNetworkErrorDialog()
        else toast(it.message ?: "无法创建房间")
    }
}

internal fun MainActivity.showLanShareNetworkErrorDialog() {
    showIos26NoticeDialogCompose("Error: 当前不处于局域网")
}

internal fun MainActivity.openLanShareCamera() {
    cameraOcrViewModel.prepareCameraRequest(MainActivity.REQUEST_LAN_SHARE_CAPTURE)
    if (checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestAppPermissions(arrayOf(Manifest.permission.CAMERA), MainActivity.REQUEST_CAMERA_PERMISSION)
        return
    }
    val photoFile = File.createTempFile("lan_share_photo_", ".jpg", cacheDir)
    val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
    cameraOcrViewModel.setCameraOutput(photoUri, photoFile)
    // 部分系统相机会忽略 EXTRA_OUTPUT 并直接写入系统图库；记录启动时刻，
    // 回退查找时只允许本次拍摄产生的媒体，避免误取上一张旧照片。
    cameraOcrViewModel.markCameraCaptureStarted()
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("output", photoUri)
    }
    try {
        launchExternalActivity(intent, MainActivity.REQUEST_LAN_SHARE_CAPTURE)
    } catch (_: Exception) {
        cameraOcrViewModel.clearCameraOutput()
        photoFile.delete()
        toast("当前设备没有可用的系统相机")
    }
}

internal fun MainActivity.findRecentLanCameraMedia(): Uri? {
    val threshold = (cameraOcrViewModel.cameraCaptureState.value.startedAtMillis - 2_000L).coerceAtLeast(0L) / 1_000L
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
        requestAppPermissions(arrayOf(permission), MainActivity.REQUEST_LAN_SHARE_GALLERY_PERMISSION)
    } else openLanShareGalleryPicker()
}

internal fun MainActivity.openLanShareGalleryPicker() {
    val intent = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES)
    else Intent(Intent.ACTION_PICK).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
    launchExternalActivity(
        intent.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },
        MainActivity.REQUEST_LAN_SHARE_UPLOAD,
    )
}

internal fun MainActivity.openLanShareFiles() {
    if (Build.VERSION.SDK_INT <= 32 &&
        checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        requestAppPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), MainActivity.REQUEST_LAN_SHARE_FILE_PERMISSION)
    } else openLanShareFilePicker()
}

internal fun MainActivity.openLanShareFilePicker() {
    launchExternalActivity(
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        },
        MainActivity.REQUEST_LAN_SHARE_UPLOAD,
    )
}

internal fun MainActivity.selectLanShareAttachment(uri: Uri, temporaryFile: File? = null, autoUpload: Boolean = false) {
    lanShareViewModel.uiState.value.pendingUploadTempFile?.takeIf { it != temporaryFile }?.delete()
    val displayName = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }.getOrNull() ?: "附件"
    lanShareViewModel.setPendingUpload(uri, temporaryFile, displayName)
    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    if (autoUpload) {
        lanShareViewModel.takePendingUpload()?.let { (selectedUri, selectedFile) ->
            lanShareViewModel.uiState.value.session?.let { session ->
                lanShareViewModel.uploadFile(session, selectedUri, selectedFile)
            }
        }
    }
    else {
        toast("已选择附件，点击发送按钮发送")
    }
}

internal fun MainActivity.joinLanShareSession(value: String) {
    lanShareViewModel.joinSessionFromAddress(value)
}

internal fun MainActivity.showLanShareQrDialog() {
    showLanShareQrDialogCompose()
}

internal fun MainActivity.closeLanShare() {
    lanShareViewModel.closeSession()
    File(cacheDir, "lan-share-preview").listFiles().orEmpty().forEach { it.delete() }
}


internal fun MainActivity.saveLanShareFile(file: LanShareFile) {
    val mime = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase())
        ?: "application/octet-stream"
    lanShareViewModel.setPendingDownloadId(file.id)
    launchExternalActivity(
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mime
            putExtra(Intent.EXTRA_TITLE, file.name)
            addCategory(Intent.CATEGORY_OPENABLE)
        },
        MainActivity.REQUEST_LAN_SHARE_DOWNLOAD,
    )
}

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
