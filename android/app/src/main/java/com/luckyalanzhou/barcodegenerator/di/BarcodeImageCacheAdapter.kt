package com.luckyalanzhou.barcodegenerator.di

import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.data.LocalBarcodeFileStore
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageCache

/** Adapts the data module's private-file store to the app presentation rendering port. */
internal class LocalBarcodeImageCacheAdapter(
    private val fileStore: LocalBarcodeFileStore,
) : BarcodeImageCache {
    override fun readImage(key: String): Bitmap? = fileStore.readImage(key)

    override fun writeImage(key: String, bitmap: Bitmap) = fileStore.writeImage(key, bitmap)
}
