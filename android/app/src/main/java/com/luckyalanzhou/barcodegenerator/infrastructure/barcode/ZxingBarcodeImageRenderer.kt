package com.luckyalanzhou.barcodegenerator.infrastructure.barcode

import android.graphics.Bitmap
import android.graphics.Color
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
import com.luckyalanzhou.barcodegenerator.presentation.shared.recordSafely
import com.luckyalanzhou.barcodegenerator.domain.barcodeRenderSize
import com.luckyalanzhou.barcodegenerator.domain.AppLogger

/** ZXing 图像渲染适配器，封装在 Presentation 图像渲染接口之后。 */
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
            val foreground = BarcodeImageColors.foreground(dark)
            val encoded = encode(
                item.text,
                format,
                style,
                dark,
                density,
                // 深色页面必须保留浅色静区，确保条码保持常规深条浅底极性。
                withBackground = dark,
                foregroundColor = foreground,
            ) ?: return null
            val image = if (formatId == BarcodeFormatIds.CODE_128) {
                addQuietZone(
                    trim(encoded, foreground),
                    if (dark) BarcodeImageColors.background(dark) else Color.TRANSPARENT,
                    foreground,
                )
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
        val foreground = BarcodeImageColors.foreground(dark)
        val encoded = encode(
            text,
            format,
            style,
            dark,
            density,
            withBackground,
            foregroundColor = foreground,
        ) ?: return null
        return if (formatId == BarcodeFormatIds.CODE_128) {
            addQuietZone(
                trim(encoded, foreground),
                if (withBackground) BarcodeImageColors.background(dark) else Color.TRANSPARENT,
                foreground,
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
        foregroundColor: Int = BarcodeImageColors.foreground(dark),
    ): Bitmap? = captureRecoverableBarcodeEncoding(
        operation = {
            val (width, barcodeHeight) = barcodeRenderSize(formatId(format), style, density)
            val matrix = MultiFormatWriter().encode(text, format, width, barcodeHeight, barcodeEncodeHints(format))
            val background = if (withBackground) BarcodeImageColors.background(dark) else Color.TRANSPARENT
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) foregroundColor else background
            }
            createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
            }
        },
        onFailure = { error ->
            logger.recordSafely(
                "barcode_render",
                "encode failed format=$format textLength=${text.length} density=$density",
                diagnosticError = error,
                primaryError = error,
            )
        },
    )

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

    private fun addQuietZone(source: Bitmap, backgroundColor: Int, foregroundColor: Int): Bitmap {
        // Code 128 静区按窄模块宽度计算，避免短码或高密度下固定条高留白不足。
        val quiet = maxOf(8, narrowestModuleWidth(source, foregroundColor) * 10)
        val paddedWidth = source.width + quiet * 2
        // 直接把条码行写入带静区的目标数组，避免同时保留源图和目标图两份整图像素数组。
        val pixels = IntArray(paddedWidth * source.height) { backgroundColor }
        var result: Bitmap? = null
        try {
            source.getPixels(pixels, quiet, paddedWidth, 0, 0, source.width, source.height)
            val padded = createBitmap(paddedWidth, source.height, Bitmap.Config.ARGB_8888)
            result = padded
            padded.setPixels(pixels, 0, paddedWidth, 0, 0, paddedWidth, source.height)
            return padded
        } catch (error: Throwable) {
            result?.let { if (!it.isRecycled) it.recycle() }
            throw error
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private fun narrowestModuleWidth(source: Bitmap, foregroundColor: Int): Int {
        val y = source.height / 2
        var moduleWidth = 0
        var runStart = 0
        var runColor = source.getPixel(0, y)
        for (x in 1 until source.width) {
            val color = source.getPixel(x, y)
            if (color != runColor) {
                moduleWidth = greatestCommonDivisor(moduleWidth, x - runStart)
                runStart = x
                runColor = color
            }
        }
        moduleWidth = greatestCommonDivisor(moduleWidth, source.width - runStart)
        check(source.getPixel(0, y) == foregroundColor && source.getPixel(source.width - 1, y) == foregroundColor)
        return moduleWidth.coerceAtLeast(1)
    }

    private tailrec fun greatestCommonDivisor(left: Int, right: Int): Int =
        if (right == 0) left else greatestCommonDivisor(right, left % right)

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

/** 为 QR 与一维格式保留规范静区；Code 128 在位图裁切后使用本类的像素静区。 */
internal fun barcodeEncodeHints(format: BarcodeFormat): Map<EncodeHintType, Any> = buildMap {
    put(EncodeHintType.MARGIN, when (format) {
        BarcodeFormat.QR_CODE -> 4
        BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A -> 9
        BarcodeFormat.CODE_128 -> 0
        else -> 10
    })
    if (format == BarcodeFormat.QR_CODE) put(EncodeHintType.CHARACTER_SET, Charsets.UTF_8.name())
}
