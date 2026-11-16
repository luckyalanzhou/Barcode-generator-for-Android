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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraOcrViewModelTest {
    @Test
    fun olderOcrCompletionCannotReplaceNewerCaptureResult() {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val messages = CopyOnWriteArrayList<String>()
        val viewModel = createViewModel(
            ocrTextGateway = object : OcrTextGateway {
                override suspend fun recognize(image: ImagePayload, confusionMask: Int): List<String> =
                    when (calls.incrementAndGet()) {
                        1 -> {
                            firstStarted.complete(Unit)
                            releaseFirst.await()
                            listOf("OLDER-CAPTURE")
                        }
                        2 -> {
                            secondStarted.complete(Unit)
                            releaseSecond.await()
                            listOf("NEWER-CAPTURE")
                        }
                        else -> error("unexpected OCR call")
                    }
            },
            logger = AppLogger { _, message, _ -> messages += message },
        )
        val firstBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val secondBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        viewModel.recognizeText(firstBitmap, confusionMask = 0)
        pumpMainUntil("first OCR request starts") { firstStarted.isCompleted }
        viewModel.recognizeText(secondBitmap, confusionMask = 0)
        pumpMainUntil("second OCR request starts") { secondStarted.isCompleted }

        releaseSecond.complete(Unit)
        pumpMainUntil("newer OCR result is emitted") { messages.any { "recognize success" in it } }
        releaseFirst.complete(Unit)
        pumpMainUntil("both OCR requests finish") {
            messages.count { message ->
                "recognize success" in message || "recognize superseded" in message ||
                    "recognize cancelled" in message || "recognize failed" in message
            } == 2
        }

        val events = runBlocking {
            val latestResult = viewModel.events.take(1).toList()
            val staleResult = withTimeoutOrNull(100) { viewModel.events.first() }
            assertNull("a stale OCR result must not be emitted", staleResult)
            latestResult
        }

        assertEquals(
            listOf(
                CameraOcrEvent.RecognizedText(listOf("NEWER-CAPTURE")),
            ),
            events,
        )
    }

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
        assertEquals(CameraOcrEvent.Notice(R.string.ocr_failed), event)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun ocrSuccessAndUserEventsSurviveOrdinaryLoggerFailures() {
        val viewModel = createViewModel(
            ocrTextGateway = object : OcrTextGateway {
                override suspend fun recognize(image: ImagePayload, confusionMask: Int) = listOf("TEST-123")
            },
            logger = AppLogger { _, _, _ -> error("diagnostic storage unavailable") },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val events = runBlocking {
            viewModel.performTextRecognition(
                bitmap,
                confusionMask = 0,
                expectedDraft = listOf("BEFORE-CAPTURE"),
            )
            viewModel.events.take(1).toList()
        }

        assertEquals(
            listOf(
                CameraOcrEvent.RecognizedText(listOf("TEST-123"), listOf("BEFORE-CAPTURE")),
            ),
            events,
        )
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun oversizedOcrResultIsRejectedBeforeItCanReplaceGenerateDraft() {
        val viewModel = createViewModel(
            ocrTextGateway = object : OcrTextGateway {
                override suspend fun recognize(image: ImagePayload, confusionMask: Int) =
                    List(101) { "TEST-$it" }
            },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val events = runBlocking {
            viewModel.performTextRecognition(bitmap, confusionMask = 0)
            viewModel.events.take(1).toList()
        }

        assertEquals(
            listOf(CameraOcrEvent.Notice(R.string.ocr_too_many_rows, 100)),
            events,
        )
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun ocrFailureNoticeSurvivesOrdinaryLoggerFailures() {
        val viewModel = createViewModel(
            ocrTextGateway = object : OcrTextGateway {
                override suspend fun recognize(image: ImagePayload, confusionMask: Int): List<String> {
                    error("recognition engine unavailable")
                }
            },
            logger = AppLogger { _, _, _ -> error("diagnostic storage unavailable") },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val event = runBlocking {
            viewModel.performTextRecognition(bitmap, confusionMask = 0)
            viewModel.events.first()
        }

        assertEquals(CameraOcrEvent.Notice(R.string.ocr_failed), event)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun ocrCancellationIsPreservedWhenCancellationDiagnosticFails() {
        val cancellation = kotlinx.coroutines.CancellationException("recognition cancelled")
        val viewModel = createViewModel(
            ocrTextGateway = object : OcrTextGateway {
                override suspend fun recognize(image: ImagePayload, confusionMask: Int): List<String> = throw cancellation
            },
            logger = AppLogger { _, message, _ ->
                if (message.contains("cancelled")) error("diagnostic storage unavailable")
            },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val caught = try {
            runBlocking { viewModel.performTextRecognition(bitmap, confusionMask = 0) }
            null
        } catch (error: Throwable) {
            error
        }

        assertSame(cancellation, caught)
        assertEquals("diagnostic storage unavailable", cancellation.suppressed.single().message)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun loggerInterruptionIsPropagatedAndRestoresThreadInterruptFlag() {
        val interruption = InterruptedException("diagnostic write interrupted")
        val viewModel = createViewModel(
            logger = AppLogger { _, message, _ ->
                if (message.contains("recognize start")) throw interruption
            },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        try {
            val caught = try {
                runBlocking { viewModel.performTextRecognition(bitmap, confusionMask = 0) }
                null
            } catch (error: Throwable) {
                error
            }

            assertSame(interruption, caught)
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(bitmap.isRecycled)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun barcodeSuccessIsReturnedWhenSuccessDiagnosticFails() {
        val viewModel = createViewModel(
            barcodeDecodeGateway = object : BarcodeDecodeGateway {
                override suspend fun decode(image: ImagePayload) = "TEST-123"
            },
            logger = AppLogger { _, message, _ ->
                if (message.contains("decode success")) error("diagnostic storage unavailable")
            },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val decoded = runBlocking { viewModel.decodeBarcode(bitmap) }

        assertEquals("TEST-123", decoded)
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

    private fun pumpMainUntil(description: String, condition: () -> Boolean) {
        val mainLooper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            mainLooper.idle()
            Thread.sleep(5)
        }
        mainLooper.idle()
        assertTrue("timed out waiting for $description", condition())
    }
}
