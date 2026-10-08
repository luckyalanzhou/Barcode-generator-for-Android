package com.luckyalanzhou.barcodegenerator.presentation.shared

import android.graphics.Bitmap

/** Presentation port for cached barcode bitmaps; the concrete store is bound by app composition. */
interface BarcodeImageCache {
    fun readImage(key: String): Bitmap?
    fun writeImage(key: String, bitmap: Bitmap)
}
