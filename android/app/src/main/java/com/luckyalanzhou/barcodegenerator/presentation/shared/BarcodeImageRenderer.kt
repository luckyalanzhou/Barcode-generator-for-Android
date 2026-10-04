package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.BarcodeImageColors


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings

/**
 * 结果页条码图像的生成和缓存边界。
 *
 * ViewModel 只负责调度，不再持有 ZXing、Bitmap 和缓存细节。
 */
class BarcodeImageRenderer(
    private val imageCache: BarcodeImageCache,
) {
    private val renderLocks = Array(64) { Any() }
    fun loadOrCreate(
        item: CodeItem,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
    ): Bitmap? {
        val format = barcodeFormats.firstOrNull { it.first == item.format }?.second ?: BarcodeFormat.CODE_128
        val key = barcodeRenderKey(item, barcodeRenderSize(format, style, density), dark)
        return synchronized(renderLocks[(key.hashCode() and Int.MAX_VALUE) % renderLocks.size]) {
        imageCache.readImage(key)?.let { return it }
        val encoded = encode(item.text, format, style, dark, density) ?: return null
        val image = if (format == BarcodeFormat.CODE_128) {
            addQuietZone(trim(encoded, BarcodeImageColors.foreground(dark)), BarcodeImageColors.background(dark))
        } else encoded
        imageCache.writeImage(key, image)
        image
        }
    }

    fun create(
        text: String,
        format: BarcodeFormat,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean = true,
    ): Bitmap? {
        val encoded = encode(text, format, style, dark, density, withBackground) ?: return null
        return if (format == BarcodeFormat.CODE_128) {
            addQuietZone(trim(encoded, BarcodeImageColors.foreground(dark)), if (withBackground) BarcodeImageColors.background(dark) else Color.TRANSPARENT)
        } else encoded
    }

    private fun encode(
        text: String,
        format: BarcodeFormat,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean = true,
    ): Bitmap? = runCatching {
        val (width, barcodeHeight) = barcodeRenderSize(format, style, density)
        val matrix = MultiFormatWriter().encode(text, format, width, barcodeHeight, mapOf(EncodeHintType.MARGIN to 0))
        val foreground = BarcodeImageColors.foreground(dark)
        val background = if (withBackground) BarcodeImageColors.background(dark) else Color.TRANSPARENT
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) foreground else background
        }
        createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }.getOrNull()

    private fun trim(source: Bitmap, foreground: Int): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        var left = source.width
        var right = -1
        for (x in 0 until source.width) {
            var hasBar = false
            for (y in 0 until source.height) {
                if (pixels[y * source.width + x] == foreground) { hasBar = true; break }
            }
            if (hasBar) { left = minOf(left, x); right = maxOf(right, x) }
        }
        return if (right >= left) Bitmap.createBitmap(source, left, 0, right - left + 1, source.height) else source
    }

    private fun addQuietZone(source: Bitmap, backgroundColor: Int): Bitmap {
        val quiet = maxOf(8, source.height / 4)
        return createBitmap(source.width + quiet * 2, source.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(backgroundColor); drawBitmap(source, quiet.toFloat(), 0f, Paint()) }
        }
    }
}
