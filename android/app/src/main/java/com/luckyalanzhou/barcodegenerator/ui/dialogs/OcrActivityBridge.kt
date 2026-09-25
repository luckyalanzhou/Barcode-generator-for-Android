package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.ui.app.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.ui.app.toast

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import org.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.*

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
        cameraOcrViewModel.prepareCameraRequest(requestCode)
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
        cameraOcrViewModel.setCameraOutput(photoUri, photoFile)
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            launchExternalActivity(intent, requestCode)
        } catch (_: Exception) {
            cameraOcrViewModel.clearCameraOutput()
            photoFile.delete()
            toast("当前设备没有可用的相机")
        }
    }



internal fun MainActivity.pickBarcodeImage() { launchExternalActivity(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 44) }


internal fun MainActivity.pickTextImage() { launchExternalActivity(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 46) }


internal fun MainActivity.recognizeText(bitmap: Bitmap) {
    cameraOcrViewModel.recognizeText(bitmap, settingsViewModel.getOcrMask())
}
