package com.luckyalanzhou.barcodegenerator.presentation.shared

import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings

/** Presentation port for generating barcode images without exposing a barcode library. */
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
