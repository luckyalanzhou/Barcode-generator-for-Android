package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.BarcodeDecodeGateway
import com.luckyalanzhou.barcodegenerator.domain.ImagePayload
import com.luckyalanzhou.barcodegenerator.domain.OcrTextGateway
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraOcrViewModelTest {
    @Test
    fun cameraOutputCanBeConsumedAndRequestIdsAreOneShot() {
        val viewModel = createViewModel()
        val outputFile = File("capture.jpg")

        viewModel.prepareCameraRequest(45)
        viewModel.setCameraOutput(null, outputFile)
        viewModel.markCameraCaptureStarted(1234L)
        viewModel.beginPermissionRequest(42)
        viewModel.beginExternalActivityRequest(45)

        assertEquals(45, viewModel.cameraCaptureState.value.requestCode)
        assertEquals(outputFile, viewModel.cameraCaptureState.value.outputFile)
        assertEquals(1234L, viewModel.cameraCaptureState.value.startedAtMillis)
        assertEquals(42, viewModel.consumePermissionRequest())
        assertEquals(0, viewModel.consumePermissionRequest())
        assertEquals(45, viewModel.consumeExternalActivityRequest())
        assertEquals(0, viewModel.consumeExternalActivityRequest())

        assertEquals(outputFile, viewModel.clearCameraOutput().outputFile)
        assertNull(viewModel.cameraCaptureState.value.outputFile)
        assertEquals(0L, viewModel.cameraCaptureState.value.startedAtMillis)
    }

    private fun createViewModel() = CameraOcrViewModel(
        object : OcrTextGateway {
            override suspend fun recognize(image: ImagePayload, confusionMask: Int) = emptyList<String>()
        },
        object : BarcodeDecodeGateway {
            override suspend fun decode(image: ImagePayload): String? = null
        },
    )
}
