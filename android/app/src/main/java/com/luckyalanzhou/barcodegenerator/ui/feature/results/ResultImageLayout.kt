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

internal fun composeResultRowImage(
    barcode: Bitmap, item: CodeItem, style: StyleSettings, dark: Boolean,
    width: Int, density: Float, fontScale: Float,
): Bitmap {
    val safeWidth = width.coerceAtLeast(1)
    val code128 = item.format == "Code 128-B"
    val barHeight = if (code128) (style.barHeight.coerceIn(30, 80) * density).roundToInt()
        else (barcode.height.toFloat() * safeWidth / barcode.width).roundToInt()
    val barWidth = if (code128) (style.barWidth.coerceIn(120f, 300f) * density).roundToInt().coerceAtMost(safeWidth)
        else safeWidth
    val label = if (code128) StaticLayout.Builder.obtain(
        resultImageLabel(item, style.showFormat), 0, resultImageLabel(item, style.showFormat).length,
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            // Labels belong to the page, not the barcode's light scanning surface.
            color = if (dark) Color.WHITE else Color.BLACK
            textSize = style.textSize.coerceIn(10f, 24f) * density * fontScale
        }, safeWidth,
    ).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build() else null
    val labelPadding = (4 * density).roundToInt()
    val height = barHeight + (label?.let { it.height + 2 * labelPadding } ?: 0)
    return createBitmap(safeWidth, height.coerceAtLeast(1)).also { result ->
        val canvas = Canvas(result)
        canvas.drawColor(resultPageBackground(dark))
        val left = (safeWidth - barWidth) / 2
        canvas.drawBitmap(barcode, null, Rect(left, 0, left + barWidth, barHeight),
            Paint().apply { isFilterBitmap = false })
        if (label != null) {
            canvas.withTranslation(y = (barHeight + labelPadding).toFloat()) { label.draw(this) }
        }
    }
}
