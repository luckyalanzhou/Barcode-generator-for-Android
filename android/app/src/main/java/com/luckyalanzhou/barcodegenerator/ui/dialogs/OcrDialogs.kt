package com.luckyalanzhou.barcodegenerator

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import android.text.*
import android.view.*
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
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
        pendingCameraRequest = requestCode
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), MainActivity.REQUEST_CAMERA_PERMISSION)
            return
        }
        launchCamera(requestCode)
    }


internal fun MainActivity.launchCamera(requestCode: Int) {
        val activity = this
        val photoFile = File.createTempFile("barcode_camera_", ".jpg", cacheDir)
        val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        pendingCameraUri = photoUri
        pendingCameraFile = photoFile
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, requestCode)
        } catch (_: Exception) {
            pendingCameraUri = null
            pendingCameraFile = null
            photoFile.delete()
            toast("当前设备没有可用的相机")
        }
    }



internal fun MainActivity.pickBarcodeImage() { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 44) }


internal fun MainActivity.pickTextImage() { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 46) }


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
        val activity = this
        val enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = ColorMatrix().apply { setSaturation(0f); val scale = 1.35f; val offset = -44.8f; set(floatArrayOf(scale, 0f, 0f, 0f, offset, 0f, scale, 0f, 0f, offset, 0f, 0f, scale, 0f, offset, 0f, 0f, 0f, 1f, 0f)) }
        Canvas(enhanced).drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        val image = InputImage.fromBitmap(enhanced, 0)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { results ->
                val recognizedText = forceOcrConfusionReplacement(results.text, settingsStore.getOcrConfusionReplacementMask())
                val normalizedText = recognizedText
                if (normalizedText.isBlank()) toast("未识别到文字，请拍摄清晰、正面的屏幕区域")
                else {
                    importRecognizedText(normalizedText)
                    toast("文字识别成功，已按行添加到输入框")
                }
            }
            .addOnFailureListener { toast("文字识别失败，请重试") }
            .addOnCompleteListener { recognizer.close(); enhanced.recycle() }
    }

/** 可选的数字优先纠错：只在用户主动开启时，将常见 OCR 混淆字符改为数字。 */
private fun forceOcrConfusionReplacement(text: String, mask: Int): String = text.map { char ->
    when {
        char in "Oo" && mask and SettingsStore.OCR_REPLACE_O_ZERO != 0 -> '0'
        char in "Iil" && mask and SettingsStore.OCR_REPLACE_I_ONE != 0 -> '1'
        char in "Ss" && mask and SettingsStore.OCR_REPLACE_S_FIVE != 0 -> '5'
        char in "Bb" && mask and SettingsStore.OCR_REPLACE_B_EIGHT != 0 -> '8'
        else -> char
    }
}.joinToString("")


internal fun MainActivity.importRecognizedText(text: String) {
        val activity = this
        val values = text.lines().filter { it.isNotBlank() }
        if (values.isEmpty()) return
        composeGenerateTextImport?.let { update ->
            update(values)
            return
        }
        // 正常入口已经由 ComposeGeneratePage 注册回调；保留一个无界面回退，避免外部系统返回时静默丢失结果。
        inputDraft = values.toMutableList()
        composeShellRevision.intValue++
    }


internal fun MainActivity.decodeBitmap(bitmap: Bitmap): String? = try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
    } catch (_: Exception) { null }

