package com.luckyalanzhou.barcodegenerator

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDecodeGateway
import com.luckyalanzhou.barcodegenerator.domain.ImagePayload
import com.luckyalanzhou.barcodegenerator.domain.OcrTextGateway
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrEvent
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrViewModel
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraOcrViewModelTest {
    @Test
    fun cameraOutputCanBeConsumedAndRequestIdsAreOneShot() {
        val viewModel = createViewModel()
        val outputFile = File("capture.jpg")

        viewModel.prepareCameraRequest(45)
        viewModel.setCameraOutput(null, outputFile)
        viewModel.markCameraCaptureStarted(1234L)
        viewModel.beginPermissionRequest(42, arrayOf("android.permission.CAMERA"))
        viewModel.beginExternalActivityRequest(45)

        assertEquals(45, viewModel.cameraCaptureState.value.requestCode)
        assertEquals(outputFile, viewModel.cameraCaptureState.value.outputFile)
        assertEquals(1234L, viewModel.cameraCaptureState.value.startedAtMillis)
        val permissionResult = viewModel.consumePermissionRequest(mapOf("android.permission.CAMERA" to true))
        assertEquals(42, permissionResult.requestCode)
        assertTrue(permissionResult.granted)
        assertEquals(0, viewModel.consumePermissionRequest(emptyMap()).requestCode)
        assertEquals(45, viewModel.consumeExternalActivityRequest())
        assertEquals(0, viewModel.consumeExternalActivityRequest())

        assertEquals(outputFile, viewModel.clearCameraOutput().outputFile)
        assertNull(viewModel.cameraCaptureState.value.outputFile)
        assertEquals(0L, viewModel.cameraCaptureState.value.startedAtMillis)
    }

    @Test
    fun ocrGatewayFailureReportsNoticeAndRecyclesBitmapWithoutEscapingCoroutine() {
        val viewModel = createViewModel(
            ocrTextGateway = object : OcrTextGateway {
                override suspend fun recognize(image: ImagePayload, confusionMask: Int): List<String> {
                    error("识别引擎暂时不可用")
                }
            },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val event = runBlocking {
            viewModel.performTextRecognition(bitmap, confusionMask = 0)
            viewModel.events.first()
        }
        assertEquals(CameraOcrEvent.Notice("文字识别失败，请重试"), event)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun barcodeGatewayFailureReturnsNoResultAndRecyclesBitmap() {
        var loggedError: Throwable? = null
        val loggedMessages = mutableListOf<String>()
        val viewModel = createViewModel(
            barcodeDecodeGateway = object : BarcodeDecodeGateway {
                override suspend fun decode(image: ImagePayload): String? {
                    error("条码识别暂时不可用")
                }
            },
            logger = AppLogger { tag, message, error ->
                loggedMessages += "$tag:$message"
                loggedError = error
            },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val decoded = runBlocking { viewModel.decodeBarcode(bitmap) }

        assertNull(decoded)
        assertEquals("条码识别暂时不可用", loggedError?.message)
        assertEquals(2, loggedMessages.size)
        assertTrue(loggedMessages[0].startsWith("barcode:decode start operation="))
        val operationId = loggedMessages[0].substringAfter("operation=")
        assertTrue(loggedMessages[1].startsWith("barcode:decode failed operation=$operationId"))
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun barcodeGatewaySuccessLogsOnlyWhetherAResultExists() {
        val loggedMessages = mutableListOf<String>()
        val viewModel = createViewModel(
            barcodeDecodeGateway = object : BarcodeDecodeGateway {
                override suspend fun decode(image: ImagePayload) = "PRIVATE-BARCODE-CONTENT"
            },
            logger = AppLogger { tag, message, _ -> loggedMessages += "$tag:$message" },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val decoded = runBlocking { viewModel.decodeBarcode(bitmap) }

        assertEquals("PRIVATE-BARCODE-CONTENT", decoded)
        assertEquals(2, loggedMessages.size)
        assertTrue(loggedMessages[0].startsWith("barcode:decode start operation="))
        val operationId = loggedMessages[0].substringAfter("operation=")
        assertTrue(loggedMessages[1].startsWith("barcode:decode success operation=$operationId resultPresent=true"))
        assertFalse(loggedMessages.joinToString(" ").contains("PRIVATE-BARCODE-CONTENT"))
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun barcodeGatewayCancellationIsLoggedAndPropagated() {
        val loggedMessages = mutableListOf<String>()
        val viewModel = createViewModel(
            barcodeDecodeGateway = object : BarcodeDecodeGateway {
                override suspend fun decode(image: ImagePayload): String? {
                    throw kotlinx.coroutines.CancellationException("cancelled")
                }
            },
            logger = AppLogger { tag, message, _ -> loggedMessages += "$tag:$message" },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var caught: kotlinx.coroutines.CancellationException? = null

        try {
            runBlocking { viewModel.decodeBarcode(bitmap) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            caught = cancelled
        }

        assertNotNull(caught)
        assertEquals(2, loggedMessages.size)
        assertTrue(loggedMessages[0].startsWith("barcode:decode start operation="))
        val operationId = loggedMessages[0].substringAfter("operation=")
        assertTrue(loggedMessages[1].startsWith("barcode:decode cancelled operation=$operationId"))
        assertTrue(bitmap.isRecycled)
    }

    private fun createViewModel(
        ocrTextGateway: OcrTextGateway = object : OcrTextGateway {
            override suspend fun recognize(image: ImagePayload, confusionMask: Int) = emptyList<String>()
        },
        barcodeDecodeGateway: BarcodeDecodeGateway = object : BarcodeDecodeGateway {
            override suspend fun decode(image: ImagePayload): String? = null
        },
        logger: AppLogger = AppLogger { _, _, _ -> },
    ) = CameraOcrViewModel(
        ocrTextGateway = ocrTextGateway,
        barcodeDecodeGateway = barcodeDecodeGateway,
        savedState = SavedStateHandle(),
        logger = logger,
    )
}
