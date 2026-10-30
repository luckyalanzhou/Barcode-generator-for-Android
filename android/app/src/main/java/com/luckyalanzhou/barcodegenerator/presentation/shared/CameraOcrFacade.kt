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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    fun beginExternalActivityRequest(requestCode: Int): Boolean = camera.beginExternalActivity(requestCode)
    fun hasPendingExternalActivityRequest(): Boolean = camera.hasPendingExternalActivity()
    fun consumeExternalActivityRequest(): Int = camera.consumeExternalActivity()
    fun beginPermissionRequest(requestCode: Int, permissions: Array<String>, cameraRequestCode: Int? = null): Boolean =
        camera.beginPermission(requestCode, permissions, cameraRequestCode)
    fun consumePermissionRequest(result: Map<String, Boolean>): PermissionRequestResult = camera.consumePermission(result)
    fun cancelPermissionRequest() = camera.cancelPermission()

    suspend fun recognizeText(bitmap: Bitmap, confusionMask: Int): List<String> =
        ocrTextGateway.recognize(bitmap.toImagePayload(), confusionMask)

    suspend fun decodeBarcode(bitmap: Bitmap): String? = barcodeDecodeGateway.decode(bitmap.toImagePayload())

    private suspend fun Bitmap.toImagePayload(): ImagePayload = try {
        withContext(Dispatchers.Default) {
            ByteArrayOutputStream().use { output ->
                check(compress(Bitmap.CompressFormat.PNG, 100, output)) { "无法读取图片内容" }
                ImagePayload(output.toByteArray())
            }
        }
    } finally {
        // 从此处开始由压缩后的载荷持有识别所需的数据。
        if (!isRecycled) recycle()
    }
}
