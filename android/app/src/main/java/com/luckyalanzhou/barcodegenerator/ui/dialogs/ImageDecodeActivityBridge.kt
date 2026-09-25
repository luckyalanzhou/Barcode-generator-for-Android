package com.luckyalanzhou.barcodegenerator.ui.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.luckyalanzhou.barcodegenerator.MainActivity

/** Decodes camera/gallery recognition images at a bounded size and applies their EXIF orientation. */
internal fun MainActivity.decodeRecognitionBitmap(uri: Uri, maxEdge: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = contentResolver.openInputStream(uri) ?: return@runCatching null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
    }
    val decoded = contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return@runCatching null

    val orientation = runCatching {
        contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    try {
        val transform = exifBitmapTransform(orientation)
        if (transform.rotationDegrees == 0 && !transform.flipHorizontally) return@runCatching decoded

        val matrix = Matrix().apply {
            if (transform.rotationDegrees != 0) postRotate(transform.rotationDegrees.toFloat())
            if (transform.flipHorizontally) postScale(-1f, 1f)
        }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { oriented ->
            if (oriented !== decoded) decoded.recycle()
        }
    } catch (error: Throwable) {
        decoded.recycle()
        throw error
    }
}.getOrNull()
