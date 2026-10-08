package com.luckyalanzhou.barcodegenerator.data.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Decodes a bounded raster preview without changing the shared source file. */
object LanShareImagePreviewDecoder {
    const val MAX_DECODE_EDGE = 4_096

    fun decode(file: File, maxEdge: Int = MAX_DECODE_EDGE): Bitmap? {
        if (!file.isFile || maxEdge <= 0) return null
        return decodeWithImageDecoder(file, maxEdge) ?: decodeWithBitmapFactory(file, maxEdge)
    }

    /** Pure sizing helper, exposed so its bounds can be verified on the JVM. */
    fun sampleSize(imageWidth: Int, imageHeight: Int, maxEdge: Int = MAX_DECODE_EDGE): Int {
        if (imageWidth <= 0 || imageHeight <= 0 || maxEdge <= 0) return 1
        val longestEdge = maxOf(imageWidth, imageHeight).toLong()
        var sampleSize = 1
        while (longestEdge > maxEdge.toLong() * sampleSize) {
            if (sampleSize > Int.MAX_VALUE / 2) return Int.MAX_VALUE
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun decodeWithImageDecoder(file: File, maxEdge: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val longestEdge = maxOf(width, height)
                if (width > 0 && height > 0 && longestEdge > maxEdge) {
                    val scale = maxEdge.toFloat() / longestEdge
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }.getOrNull()
    }

    private fun decodeWithBitmapFactory(file: File, maxEdge: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            },
        ) ?: return@runCatching null
        applyExifOrientation(file, bitmap)
    }.getOrNull()

    /** ImageDecoder auto-orients on API 28+; correct EXIF rotation on older Android versions. */
    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            else -> 0f
        }
        return if (rotation == 0f) bitmap else runCatching {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(rotation) },
                true,
            )
        }.getOrDefault(bitmap)
    }
}
