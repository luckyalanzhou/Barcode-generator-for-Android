package com.luckyalanzhou.barcodegenerator.presentation.shared

import android.graphics.Bitmap

/** 页面层访问缓存条码位图的接口；具体存储由应用组装时绑定。 */
interface BarcodeImageCache {
    fun readImage(key: String): Bitmap?
    fun writeImage(key: String, bitmap: Bitmap)
}
