package com.luckyalanzhou.barcodegenerator.ui.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.ui.dialogs.calculateInSampleSize
import com.luckyalanzhou.barcodegenerator.ui.dialogs.exifBitmapTransform
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import kotlinx.coroutines.CancellationException

/** Decodes camera/gallery recognition images at a bounded size and applies their EXIF orientation. */
internal fun MainActivity.decodeRecognitionBitmap(uri: Uri, maxEdge: Int): Bitmap? {
    var decodedBitmap: Bitmap? = null
    return captureRecoverableRecognitionImageDecode(
        operation = decode@{
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = contentResolver.openInputStream(uri) ?: return@decode null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@decode null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            }
            val decoded = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@decode null
            decodedBitmap = decoded

            val orientation = try {
                contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                null
            } ?: ExifInterface.ORIENTATION_NORMAL

            val transform = exifBitmapTransform(orientation)
            if (transform.rotationDegrees == 0 && !transform.flipHorizontally) {
                decodedBitmap = null
                return@decode decoded
            }

            val matrix = Matrix().apply {
                if (transform.rotationDegrees != 0) postRotate(transform.rotationDegrees.toFloat())
                if (transform.flipHorizontally) postScale(-1f, 1f)
            }
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (oriented !== decoded) decoded.recycle()
            decodedBitmap = null
            oriented
        },
        cleanup = {
            decodedBitmap?.let { if (!it.isRecycled) it.recycle() }
        },
        onFailure = { error ->
            // 不写 URI、文件名或图片内容，避免导出的诊断日志泄漏用户数据。
            DebugLog.record("image_decode", "recognition decoder exception=${error.javaClass.simpleName}")
        },
    )
}
