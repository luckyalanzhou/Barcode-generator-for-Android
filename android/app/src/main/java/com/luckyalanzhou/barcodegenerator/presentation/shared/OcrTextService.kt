package com.luckyalanzhou.barcodegenerator.presentation.shared


import com.luckyalanzhou.barcodegenerator.domain.OcrCorrectionMask

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** OCR 数据服务；负责图像增强、ML Kit 生命周期和字符纠错，不发布 UI 事件。 */
class OcrTextService {
    suspend fun recognize(bitmap: Bitmap, confusionMask: Int): List<String> = suspendCancellableCoroutine { continuation ->
        val enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            set(floatArrayOf(1.35f, 0f, 0f, 0f, -44.8f, 0f, 1.35f, 0f, 0f, -44.8f, 0f, 0f, 1.35f, 0f, -44.8f, 0f, 0f, 0f, 1f, 0f))
        }
        Canvas(enhanced).drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(InputImage.fromBitmap(enhanced, 0))
            .addOnSuccessListener { results ->
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
            }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(emptyList()) }
            .addOnCompleteListener {
                recognizer.close()
                enhanced.recycle()
            }
        continuation.invokeOnCancellation { recognizer.close(); enhanced.recycle() }
    }
}
