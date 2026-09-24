package com.luckyalanzhou.barcodegenerator.presentation.shared

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDecodeGateway
import com.luckyalanzhou.barcodegenerator.domain.ImagePayload
import com.luckyalanzhou.barcodegenerator.domain.OcrTextGateway
import com.luckyalanzhou.barcodegenerator.presentation.CameraCaptureState
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/** 相机请求、OCR 和条码识别的组合边界，不包含 Compose 或 Activity 逻辑。 */
internal class CameraOcrFacade(
    private val ocrTextGateway: OcrTextGateway,
    private val barcodeDecodeGateway: BarcodeDecodeGateway,
    savedState: SavedStateHandle,
) {
    private val camera = CameraRequestCoordinator(savedState)
    val cameraState: StateFlow<CameraCaptureState> = camera.state

    fun prepareCameraRequest(requestCode: Int) = camera.prepare(requestCode)
    fun setCameraOutput(uri: Uri?, file: File?) = camera.setOutput(uri, file)
    fun markCameraCaptureStarted(nowMillis: Long) = camera.markStarted(nowMillis)
    fun clearCameraOutput(): CameraCaptureState = camera.clearOutput()
    fun beginExternalActivityRequest(requestCode: Int) = camera.beginExternalActivity(requestCode)
    fun consumeExternalActivityRequest(): Int = camera.consumeExternalActivity()
    fun beginPermissionRequest(requestCode: Int) = camera.beginPermission(requestCode)
    fun consumePermissionRequest(): Int = camera.consumePermission()

    suspend fun recognizeText(bitmap: Bitmap, confusionMask: Int): List<String> =
        ocrTextGateway.recognize(bitmap.toImagePayload(), confusionMask)

    suspend fun decodeBarcode(bitmap: Bitmap): String? = barcodeDecodeGateway.decode(bitmap.toImagePayload())

    private fun Bitmap.toImagePayload(): ImagePayload = ByteArrayOutputStream().use { output ->
        check(compress(Bitmap.CompressFormat.PNG, 100, output)) { "无法读取图片内容" }
        ImagePayload(output.toByteArray())
    }
}
