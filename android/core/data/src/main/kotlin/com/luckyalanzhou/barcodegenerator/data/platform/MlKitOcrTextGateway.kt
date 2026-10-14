package com.luckyalanzhou.barcodegenerator.data.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.luckyalanzhou.barcodegenerator.domain.ImagePayload
import com.luckyalanzhou.barcodegenerator.domain.OcrCorrectionMask
import com.luckyalanzhou.barcodegenerator.domain.OcrTextGateway
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Android/ML Kit adapter; OCR remains outside presentation and domain. */
class MlKitOcrTextGateway : OcrTextGateway {
    override suspend fun recognize(image: ImagePayload, confusionMask: Int): List<String> {
        val pendingEnhanced = AtomicReference<Bitmap?>(null)
        val enhanced = try {
            withContext(Dispatchers.Default) {
                val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                    ?: return@withContext null
                var enhancedBitmap: Bitmap? = null
                try {
                    val enhancedTarget = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        ?: return@withContext null
                    enhancedBitmap = enhancedTarget
                    val matrix = ColorMatrix().apply {
                        setSaturation(0f)
                        set(floatArrayOf(1.35f, 0f, 0f, 0f, -44.8f, 0f, 1.35f, 0f, 0f, -44.8f, 0f, 0f, 1.35f, 0f, -44.8f, 0f, 0f, 0f, 1f, 0f))
                    }
                    Canvas(enhancedTarget).drawBitmap(
                        bitmap,
                        0f,
                        0f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) },
                    )
                    enhancedTarget.also(pendingEnhanced::set)
                } catch (error: Throwable) {
                    enhancedBitmap?.recycle()
                    throw error
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (error: Throwable) {
            pendingEnhanced.getAndSet(null)?.recycle()
            throw error
        } ?: return emptyList()
        pendingEnhanced.compareAndSet(enhanced, null)

        val recognizer = try {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } catch (_: Throwable) {
            if (!enhanced.isRecycled) enhanced.recycle()
            return emptyList()
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                recognizer.process(InputImage.fromBitmap(enhanced, 0))
                    .addOnSuccessListener { results ->
                        try {
                            val normalized = results.text.map { char ->
                                when {
                                    char in "Oo" && confusionMask and OcrCorrectionMask.O_ZERO != 0 -> '0'
                                    char in "Iil" && confusionMask and OcrCorrectionMask.I_ONE != 0 -> '1'
                                    char in "Ss" && confusionMask and OcrCorrectionMask.S_FIVE != 0 -> '5'
                                    char in "Bb" && confusionMask and OcrCorrectionMask.B_EIGHT != 0 -> '8'
                                    else -> char
                                }
                            }.joinToString("").lines().filter { it.isNotBlank() }
                            if (continuation.isActive) continuation.resume(normalized)
                        } catch (_: Throwable) {
                            if (continuation.isActive) continuation.resume(emptyList())
                        }
                    }
                    .addOnFailureListener { if (continuation.isActive) continuation.resume(emptyList()) }
                    .addOnCompleteListener {
                        recognizer.close()
                        if (!enhanced.isRecycled) enhanced.recycle()
                    }
            } catch (error: Throwable) {
                recognizer.close()
                if (!enhanced.isRecycled) enhanced.recycle()
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
    }
}
