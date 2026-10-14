package com.luckyalanzhou.barcodegenerator.ui.feature.results

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.luckyalanzhou.barcodegenerator.BarcodeImageColors
import com.luckyalanzhou.barcodegenerator.BarcodeFormatIds
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.domain.barcodeRenderSize
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import kotlin.math.roundToInt

/** The screen and PNG export use the same label, scaled bar bounds and spacing. */
internal fun resultImageLabel(item: CodeItem, showFormat: Boolean): String =
    if (showFormat) "${item.text} · ${item.format}" else item.text

internal fun resultImageSpacing(margin: Int, density: Float): Int =
    (margin.coerceIn(0, 10) * density).roundToInt()

internal fun resultPageBackground(dark: Boolean): Int =
    if (dark) Color.BLACK else BarcodeImageColors.background(false)

internal data class ResultImageRowSize(val width: Int, val height: Int)

/** Computes the exact row bounds without allocating the barcode or row bitmap. */
internal fun resultImageRowSize(
    item: CodeItem,
    style: StyleSettings,
    width: Int,
    density: Float,
    fontScale: Float,
): ResultImageRowSize {
    val formatId = barcodeFormats.firstOrNull { it.displayName == item.format }?.id ?: BarcodeFormatIds.CODE_128
    val (barcodeWidth, barcodeHeight) = barcodeRenderSize(formatId, style, density)
    return resultImageRowSize(item, style, barcodeWidth, barcodeHeight, width, density, fontScale)
}

private fun resultImageRowSize(
    item: CodeItem,
    style: StyleSettings,
    barcodeWidth: Int,
    barcodeHeight: Int,
    width: Int,
    density: Float,
    fontScale: Float,
): ResultImageRowSize {
    val safeWidth = width.coerceAtLeast(1)
    val code128 = item.format == "Code 128-B"
    val barHeight = if (code128) (style.barHeight.coerceIn(30, 80) * density).roundToInt()
        else (barcodeHeight.toFloat() * safeWidth / barcodeWidth.coerceAtLeast(1)).roundToInt()
    val label = resultLabelLayout(
        item = item,
        style = style,
        dark = false,
        width = safeWidth,
        density = density,
        fontScale = fontScale,
    )
    val labelPadding = (4 * density).roundToInt()
    return ResultImageRowSize(
        width = safeWidth,
        height = barHeight + (label?.let { it.height + 2 * labelPadding } ?: 0),
    )
}

internal fun composeResultRowImage(
    barcode: Bitmap, item: CodeItem, style: StyleSettings, dark: Boolean,
    width: Int, density: Float, fontScale: Float,
): Bitmap {
    val size = resultImageRowSize(item, style, barcode.width, barcode.height, width, density, fontScale)
    val safeWidth = size.width
    val barWidth = if (item.format == "Code 128-B") (style.barWidth.coerceIn(120f, 300f) * density).roundToInt().coerceAtMost(safeWidth)
        else safeWidth
    val label = resultLabelLayout(item, style, dark, safeWidth, density, fontScale)
    val labelPadding = (4 * density).roundToInt()
    val barHeight = if (item.format == "Code 128-B") (style.barHeight.coerceIn(30, 80) * density).roundToInt()
        else (barcode.height.toFloat() * safeWidth / barcode.width.coerceAtLeast(1)).roundToInt()
    val result = createBitmap(safeWidth, size.height.coerceAtLeast(1))
    try {
        val canvas = Canvas(result)
        canvas.drawColor(resultPageBackground(dark))
        val left = (safeWidth - barWidth) / 2
        canvas.drawBitmap(barcode, null, Rect(left, 0, left + barWidth, barHeight),
            Paint().apply { isFilterBitmap = false })
        if (label != null) {
            canvas.withTranslation(y = (barHeight + labelPadding).toFloat()) { label.draw(this) }
        }
        return result
    } catch (error: Throwable) {
        if (!result.isRecycled) result.recycle()
        throw error
    }
}

private fun resultLabelLayout(
    item: CodeItem,
    style: StyleSettings,
    dark: Boolean,
    width: Int,
    density: Float,
    fontScale: Float,
): StaticLayout? {
    if (item.format != "Code 128-B") return null
    val labelText = resultImageLabel(item, style.showFormat)
    return StaticLayout.Builder.obtain(
        labelText,
        0,
        labelText.length,
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            // Labels belong to the page, not the barcode's light scanning surface.
            color = if (dark) Color.WHITE else Color.BLACK
            textSize = style.textSize.coerceIn(10f, 24f) * density * fontScale
        },
        width,
    ).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
}
