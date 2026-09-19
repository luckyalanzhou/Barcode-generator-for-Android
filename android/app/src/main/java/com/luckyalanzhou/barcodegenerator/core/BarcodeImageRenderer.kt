package com.luckyalanzhou.barcodegenerator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.luckyalanzhou.barcodegenerator.data.LocalBarcodeFileStore
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import kotlin.math.roundToInt

/**
 * 结果页条码图像的生成和缓存边界。
 *
 * ViewModel 只负责调度，不再持有 ZXing、Bitmap 和缓存细节。
 */
class BarcodeImageRenderer(
    private val fileStore: LocalBarcodeFileStore,
) {
    fun loadOrCreate(
        item: CodeItem,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
    ): Bitmap? {
        val width = style.barWidth.toInt().coerceIn(120, 360)
        val height = style.barHeight.coerceIn(30, 150).coerceAtLeast(1)
        val textSize = style.textSize.coerceIn(10f, 24f)
        val key = fileStore.imageKey(item, width, height, textSize, style.showFormat, dark)
        fileStore.readImage(key)?.let { return it }
        val format = barcodeFormats.firstOrNull { it.first == item.format }?.second ?: BarcodeFormat.CODE_128
        val encoded = encode(item.text, format, style, dark, density) ?: return null
        val image = if (item.format == "Code 128-B") {
            addQuietZone(trim(encoded), if (dark) Color.WHITE else Color.TRANSPARENT)
        } else encoded
        fileStore.writeImage(key, image)
        return image
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
            addQuietZone(trim(encoded), if (withBackground) Color.WHITE else Color.TRANSPARENT)
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
        val code128 = format == BarcodeFormat.CODE_128
        val width = if (code128) (style.barWidth.roundToInt().coerceIn(120, 360) * density).roundToInt().coerceAtLeast(1) else 500
        val barcodeHeight = if (code128) (style.barHeight.coerceIn(30, 150) * density).roundToInt().coerceAtLeast(1)
        else if (format == BarcodeFormat.QR_CODE) 500 else 200
        val matrix = MultiFormatWriter().encode(text, format, width, barcodeHeight, mapOf(EncodeHintType.MARGIN to 0))
        val paint = Paint().apply { color = if (dark) Color.BLACK else style.barColor }
        Bitmap.createBitmap(width, barcodeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(if (withBackground) (if (dark) Color.WHITE else style.bgColor) else Color.TRANSPARENT)
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                if (matrix[x, y]) canvas.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), paint)
            }
        }
    }.getOrNull()

    private fun trim(source: Bitmap): Bitmap {
        var left = source.width
        var right = -1
        for (x in 0 until source.width) {
            var hasBar = false
            for (y in 0 until source.height) {
                val pixel = source.getPixel(x, y)
                val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                if (Color.alpha(pixel) > 0 && luminance < 200) { hasBar = true; break }
            }
            if (hasBar) { left = minOf(left, x); right = maxOf(right, x) }
        }
        return if (right >= left) Bitmap.createBitmap(source, left, 0, right - left + 1, source.height) else source
    }

    private fun addQuietZone(source: Bitmap, backgroundColor: Int): Bitmap {
        val quiet = maxOf(8, source.height / 4)
        return Bitmap.createBitmap(source.width + quiet * 2, source.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(backgroundColor); drawBitmap(source, quiet.toFloat(), 0f, Paint()) }
        }
    }
}
