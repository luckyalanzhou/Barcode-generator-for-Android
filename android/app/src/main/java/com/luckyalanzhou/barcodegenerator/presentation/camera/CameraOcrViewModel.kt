package com.luckyalanzhou.barcodegenerator.presentation.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDecodeGateway
import com.luckyalanzhou.barcodegenerator.domain.OcrTextGateway
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.MAX_GENERATE_INPUT_ROWS
import kotlinx.coroutines.CancellationException
import com.luckyalanzhou.barcodegenerator.presentation.CameraCaptureState
import com.luckyalanzhou.barcodegenerator.presentation.shared.CameraOcrFacade
import com.luckyalanzhou.barcodegenerator.presentation.shared.PermissionRequestResult
import com.luckyalanzhou.barcodegenerator.presentation.shared.recordSafely
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CameraOcrViewModel @Inject constructor(
    ocrTextGateway: OcrTextGateway,
    barcodeDecodeGateway: BarcodeDecodeGateway,
    savedState: SavedStateHandle,
    private val logger: AppLogger,
) : ViewModel() {
    private val cameraOcr = CameraOcrFacade(ocrTextGateway, barcodeDecodeGateway, savedState)
    val cameraCaptureState: StateFlow<CameraCaptureState> = cameraOcr.cameraState

    private val _events = Channel<CameraOcrEvent>(Channel.BUFFERED)
    val events: Flow<CameraOcrEvent> = _events.receiveAsFlow()

    fun prepareCameraRequest(requestCode: Int) = cameraOcr.prepareCameraRequest(requestCode)
    fun setCameraOutput(uri: Uri?, file: File?) = cameraOcr.setCameraOutput(uri, file)
    fun markCameraCaptureStarted(nowMillis: Long = System.currentTimeMillis()) =
        cameraOcr.markCameraCaptureStarted(nowMillis)
    fun clearCameraOutput(): CameraCaptureState = cameraOcr.clearCameraOutput()
    fun beginExternalActivityRequest(requestCode: Int): Boolean = cameraOcr.beginExternalActivityRequest(requestCode)
    fun hasPendingExternalActivityRequest(): Boolean = cameraOcr.hasPendingExternalActivityRequest()
    fun consumeExternalActivityRequest(): Int = cameraOcr.consumeExternalActivityRequest()
    internal fun beginPermissionRequest(requestCode: Int, permissions: Array<String>, cameraRequestCode: Int? = null): Boolean =
        cameraOcr.beginPermissionRequest(requestCode, permissions, cameraRequestCode)
    internal fun consumePermissionRequest(result: Map<String, Boolean>): PermissionRequestResult = cameraOcr.consumePermissionRequest(result)
    internal fun cancelPermissionRequest() = cameraOcr.cancelPermissionRequest()

    fun recognizeText(bitmap: Bitmap, confusionMask: Int) {
        val job = viewModelScope.launch { performTextRecognition(bitmap, confusionMask) }
        job.invokeOnCompletion { if (!bitmap.isRecycled) bitmap.recycle() }
    }

    internal suspend fun performTextRecognition(bitmap: Bitmap, confusionMask: Int) {
        val operation = System.nanoTime()
        try {
            logger.recordSafely("ocr", "recognize start operation=$operation width=${bitmap.width} height=${bitmap.height}")
            val lines = cameraOcr.recognizeText(bitmap, confusionMask)
            logger.recordSafely("ocr", "recognize success operation=$operation lineCount=${lines.size}")
            if (lines.isEmpty()) {
                _events.send(CameraOcrEvent.Notice("未识别到文字，请拍摄清晰、正面的屏幕区域"))
            } else if (lines.size > MAX_GENERATE_INPUT_ROWS) {
                _events.send(CameraOcrEvent.Notice("识别结果超过 $MAX_GENERATE_INPUT_ROWS 行，请分次拍摄"))
            } else {
                _events.send(CameraOcrEvent.RecognizedText(lines))
                _events.send(CameraOcrEvent.Notice("文字识别成功，已按行添加到输入框"))
            }
        } catch (cancelled: CancellationException) {
            logger.recordSafely("ocr", "recognize cancelled operation=$operation", primaryError = cancelled)
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.recordSafely("ocr", "recognize interrupted operation=$operation", interrupted, interrupted)
            throw interrupted
        } catch (error: Exception) {
            logger.recordSafely("ocr", "recognize failed operation=$operation", error, error)
            _events.send(CameraOcrEvent.Notice("文字识别失败，请重试"))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    suspend fun decodeBarcode(bitmap: Bitmap): String? {
        val operation = System.nanoTime()
        return try {
            logger.recordSafely("barcode", "decode start operation=$operation")
            val decoded = cameraOcr.decodeBarcode(bitmap)
            logger.recordSafely("barcode", "decode success operation=$operation resultPresent=${decoded != null}")
            decoded
        } catch (cancelled: CancellationException) {
            logger.recordSafely("barcode", "decode cancelled operation=$operation", primaryError = cancelled)
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.recordSafely("barcode", "decode interrupted operation=$operation", interrupted, interrupted)
            throw interrupted
        } catch (error: Exception) {
            logger.recordSafely("barcode", "decode failed operation=$operation", error, error)
            null
        }
    }
}

sealed interface CameraOcrEvent {
    data class RecognizedText(val lines: List<String>) : CameraOcrEvent
    data class Notice(val message: String) : CameraOcrEvent
}
