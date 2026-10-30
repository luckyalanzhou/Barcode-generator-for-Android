package com.luckyalanzhou.barcodegenerator.presentation.shared

import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings

/** 页面层生成条码图片的接口，不暴露具体条码库。 */
interface BarcodeImageRenderer {
    fun loadOrCreate(item: CodeItem, style: StyleSettings, dark: Boolean, density: Float): Bitmap?

    fun create(
        text: String,
        formatId: String,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean = true,
    ): Bitmap?
}
