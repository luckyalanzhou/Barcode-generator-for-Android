package com.luckyalanzhou.barcodegenerator.presentation.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDecodeGateway
import com.luckyalanzhou.barcodegenerator.domain.OcrTextGateway
import com.luckyalanzhou.barcodegenerator.presentation.CameraCaptureState
import com.luckyalanzhou.barcodegenerator.presentation.shared.CameraOcrFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CameraOcrViewModel @Inject constructor(
    ocrTextGateway: OcrTextGateway,
    barcodeDecodeGateway: BarcodeDecodeGateway,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val cameraOcr = CameraOcrFacade(ocrTextGateway, barcodeDecodeGateway, savedState)
    val cameraCaptureState: StateFlow<CameraCaptureState> = cameraOcr.cameraState

    private val _events = MutableSharedFlow<CameraOcrEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CameraOcrEvent> = _events.asSharedFlow()

    fun prepareCameraRequest(requestCode: Int) = cameraOcr.prepareCameraRequest(requestCode)
    fun setCameraOutput(uri: Uri?, file: File?) = cameraOcr.setCameraOutput(uri, file)
    fun markCameraCaptureStarted(nowMillis: Long = System.currentTimeMillis()) =
        cameraOcr.markCameraCaptureStarted(nowMillis)
    fun clearCameraOutput(): CameraCaptureState = cameraOcr.clearCameraOutput()
    fun beginExternalActivityRequest(requestCode: Int) = cameraOcr.beginExternalActivityRequest(requestCode)
    fun consumeExternalActivityRequest(): Int = cameraOcr.consumeExternalActivityRequest()
    fun beginPermissionRequest(requestCode: Int) = cameraOcr.beginPermissionRequest(requestCode)
    fun consumePermissionRequest(): Int = cameraOcr.consumePermissionRequest()

    fun recognizeText(bitmap: Bitmap, confusionMask: Int) {
        val job = viewModelScope.launch {
            try {
                val lines = cameraOcr.recognizeText(bitmap, confusionMask)
                if (lines.isEmpty()) {
                    _events.emit(CameraOcrEvent.Notice("未识别到文字，请拍摄清晰、正面的屏幕区域"))
                } else {
                    _events.emit(CameraOcrEvent.RecognizedText(lines))
                    _events.emit(CameraOcrEvent.Notice("文字识别成功，已按行添加到输入框"))
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        job.invokeOnCompletion { if (!bitmap.isRecycled) bitmap.recycle() }
    }

    suspend fun decodeBarcode(bitmap: Bitmap): String? = cameraOcr.decodeBarcode(bitmap)
}

sealed interface CameraOcrEvent {
    data class RecognizedText(val lines: List<String>) : CameraOcrEvent
    data class Notice(val message: String) : CameraOcrEvent
}
