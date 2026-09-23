package com.luckyalanzhou.barcodegenerator.presentation.shared

import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.data.LocalBarcodeFileStore

/** Presentation-side boundary for cached barcode bitmaps. */
interface BarcodeImageCache {
    fun readImage(key: String): Bitmap?
    fun writeImage(key: String, bitmap: Bitmap)
}

/** Adapts the data module's private-file implementation to the image-rendering contract. */
internal class LocalBarcodeImageCache(
    private val fileStore: LocalBarcodeFileStore,
) : BarcodeImageCache {
    override fun readImage(key: String): Bitmap? = fileStore.readImage(key)

    override fun writeImage(key: String, bitmap: Bitmap) = fileStore.writeImage(key, bitmap)
}
