package com.luckyalanzhou.barcodegenerator.infrastructure.barcode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.luckyalanzhou.barcodegenerator.BarcodeFormatIds
import com.luckyalanzhou.barcodegenerator.BarcodeImageColors
import com.luckyalanzhou.barcodegenerator.barcodeFormatId
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageCache
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageRenderer
import com.luckyalanzhou.barcodegenerator.presentation.shared.barcodeRenderKey
import com.luckyalanzhou.barcodegenerator.domain.barcodeRenderSize
import com.luckyalanzhou.barcodegenerator.domain.AppLogger

/** ZXing-backed adapter kept behind the presentation image-rendering port. */
class ZxingBarcodeImageRenderer(
    private val imageCache: BarcodeImageCache,
    private val logger: AppLogger,
) : BarcodeImageRenderer {
    private val renderLocks = Array(64) { Any() }

    override fun loadOrCreate(item: CodeItem, style: StyleSettings, dark: Boolean, density: Float): Bitmap? {
        val formatId = barcodeFormatId(item.format)
        val format = zxingFormat(formatId) ?: BarcodeFormat.CODE_128
        val key = barcodeRenderKey(item, barcodeRenderSize(formatId, style, density), dark)
        return synchronized(renderLocks[(key.hashCode() and Int.MAX_VALUE) % renderLocks.size]) {
            imageCache.readImage(key)?.let { return it }
            val encoded = encode(item.text, format, style, dark, density) ?: return null
            val image = if (formatId == BarcodeFormatIds.CODE_128) {
                addQuietZone(trim(encoded, BarcodeImageColors.foreground(dark)), BarcodeImageColors.background(dark))
            } else encoded
            imageCache.writeImage(key, image)
            image
        }
    }

    override fun create(
        text: String,
        formatId: String,
        style: StyleSettings,
        dark: Boolean,
        density: Float,
        withBackground: Boolean,
    ): Bitmap? {
        val format = zxingFormat(formatId) ?: return null
        val encoded = encode(text, format, style, dark, density, withBackground) ?: return null
        return if (formatId == BarcodeFormatIds.CODE_128) {
            addQuietZone(
                trim(encoded, BarcodeImageColors.foreground(dark)),
                if (withBackground) BarcodeImageColors.background(dark) else Color.TRANSPARENT,
            )
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
        val (width, barcodeHeight) = barcodeRenderSize(formatId(format), style, density)
        val matrix = MultiFormatWriter().encode(text, format, width, barcodeHeight, mapOf(EncodeHintType.MARGIN to 0))
        val foreground = BarcodeImageColors.foreground(dark)
        val background = if (withBackground) BarcodeImageColors.background(dark) else Color.TRANSPARENT
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) foreground else background
        }
        createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }.onFailure { error ->
        logger.record("barcode_render", "encode failed format=$format textLength=${text.length} density=$density", error)
    }.getOrNull()

    private fun trim(source: Bitmap, foreground: Int): Bitmap {
        var completed = false
        try {
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
            if (right < left) {
                completed = true
                return source
            }
            val trimmed = Bitmap.createBitmap(source, left, 0, right - left + 1, source.height)
            if (trimmed !== source) source.recycle()
            completed = true
            return trimmed
        } finally {
            if (!completed && !source.isRecycled) source.recycle()
        }
    }

    private fun addQuietZone(source: Bitmap, backgroundColor: Int): Bitmap {
        val quiet = maxOf(8, source.height / 4)
        var result: Bitmap? = null
        try {
            val padded = createBitmap(source.width + quiet * 2, source.height, Bitmap.Config.ARGB_8888)
            result = padded
            Canvas(padded).apply {
                drawColor(backgroundColor)
                drawBitmap(source, quiet.toFloat(), 0f, Paint())
            }
            return padded
        } catch (error: Throwable) {
            result?.let { if (!it.isRecycled) it.recycle() }
            throw error
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private fun zxingFormat(id: String): BarcodeFormat? = when (id) {
        BarcodeFormatIds.CODE_128 -> BarcodeFormat.CODE_128
        BarcodeFormatIds.QR_CODE -> BarcodeFormat.QR_CODE
        BarcodeFormatIds.CODE_39 -> BarcodeFormat.CODE_39
        BarcodeFormatIds.EAN_13 -> BarcodeFormat.EAN_13
        BarcodeFormatIds.EAN_8 -> BarcodeFormat.EAN_8
        BarcodeFormatIds.UPC_A -> BarcodeFormat.UPC_A
        BarcodeFormatIds.ITF_14 -> BarcodeFormat.ITF
        BarcodeFormatIds.CODABAR -> BarcodeFormat.CODABAR
        else -> null
    }

    private fun formatId(format: BarcodeFormat): String = when (format) {
        BarcodeFormat.CODE_128 -> BarcodeFormatIds.CODE_128
        BarcodeFormat.QR_CODE -> BarcodeFormatIds.QR_CODE
        BarcodeFormat.CODE_39 -> BarcodeFormatIds.CODE_39
        BarcodeFormat.EAN_13 -> BarcodeFormatIds.EAN_13
        BarcodeFormat.EAN_8 -> BarcodeFormatIds.EAN_8
        BarcodeFormat.UPC_A -> BarcodeFormatIds.UPC_A
        BarcodeFormat.ITF -> BarcodeFormatIds.ITF_14
        BarcodeFormat.CODABAR -> BarcodeFormatIds.CODABAR
        else -> BarcodeFormatIds.CODE_128
    }
}
