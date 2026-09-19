package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.ui.*

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import android.text.*
import android.view.*
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import org.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.*
import kotlin.math.roundToInt

internal fun MainActivity.scanWithCamera() {
        val activity = this
        openCamera(43)
    }


internal fun MainActivity.captureText() {
        val activity = this
        openCamera(MainActivity.REQUEST_TEXT_CAMERA)
}


internal fun MainActivity.openCamera(requestCode: Int) {
        val activity = this
        viewModel.prepareCameraRequest(requestCode)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestAppPermissions(arrayOf(Manifest.permission.CAMERA), MainActivity.REQUEST_CAMERA_PERMISSION)
            return
        }
        launchCamera(requestCode)
    }


internal fun MainActivity.launchCamera(requestCode: Int) {
        val activity = this
        val photoFile = File.createTempFile("barcode_camera_", ".jpg", cacheDir)
        val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        viewModel.setCameraOutput(photoUri, photoFile)
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            launchExternalActivity(intent, requestCode)
        } catch (_: Exception) {
            viewModel.clearCameraOutput()
            photoFile.delete()
            toast("当前设备没有可用的相机")
        }
    }



internal fun MainActivity.pickBarcodeImage() { launchExternalActivity(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 44) }


internal fun MainActivity.pickTextImage() { launchExternalActivity(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 46) }


/**
 * 拍摄电脑屏幕时相机经常把方向写在 EXIF 中，且原图可能大到让 OCR 处理变慢。
 * 先按 EXIF 校正，再限制最长边，保证屏幕文字以正确方向和稳定尺寸交给 ML Kit。
 */
internal fun MainActivity.prepareTextBitmap(bitmap: Bitmap, sourceFile: File?): Bitmap {
    var prepared = bitmap
    val orientation = sourceFile?.let {
        runCatching { ExifInterface(it.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
        else -> 0f
    }
    if (rotation != 0f) {
        prepared = runCatching {
            Bitmap.createBitmap(prepared, 0, 0, prepared.width, prepared.height, Matrix().apply { postRotate(rotation) }, true)
        }.getOrDefault(prepared)
    }
    val longest = maxOf(prepared.width, prepared.height)
    if (longest > 2400) {
        val scale = 2400f / longest.toFloat()
        prepared = Bitmap.createScaledBitmap(prepared, (prepared.width * scale).roundToInt(), (prepared.height * scale).roundToInt(), true)
    }
    return prepared
}


internal fun MainActivity.recognizeText(bitmap: Bitmap) {
    viewModel.recognizeText(bitmap, settingsViewModel.getOcrMask())
}
