package com.luckyalanzhou.barcodegenerator.di

import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.data.LocalBarcodeFileStore
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageCache

/** 将 Data 模块的私有文件存储适配到 App Presentation 的图像渲染接口。 */
internal class LocalBarcodeImageCacheAdapter(
    private val fileStore: LocalBarcodeFileStore,
) : BarcodeImageCache {
    override fun readImage(key: String): Bitmap? = fileStore.readImage(key)

    override fun writeImage(key: String, bitmap: Bitmap) = fileStore.writeImage(key, bitmap)
}
