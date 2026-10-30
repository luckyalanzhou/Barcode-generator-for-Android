package com.luckyalanzhou.barcodegenerator.ui.feature.results

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.luckyalanzhou.barcodegenerator.BarcodeFormatIds
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.domain.barcodeRenderSize
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.appColorScheme
import kotlin.math.roundToInt

/** 页面显示与 PNG 导出统一只显示条码内容。 */
internal fun resultImageLabel(item: CodeItem): String = item.text

internal fun resultImageSpacing(margin: Int, density: Float): Int =
    (margin.coerceIn(0, 10) * density).roundToInt()

internal fun resultPageBackground(dark: Boolean): Int =
    appColorScheme(dark).surfaces.background.toArgb()

internal data class ResultImageRowSize(val width: Int, val height: Int)

/** 预览行保留页面画布的精确颜色；QR 原始像素宽度限制负责控制大批量内存。 */
internal fun resultPreviewBitmapConfig(): Bitmap.Config = Bitmap.Config.ARGB_8888

/** 不创建条码或行位图，直接计算每一行的实际边界。 */
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
    bitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888,
    rasterWidthLimit: Int? = null,
): Bitmap {
    // 屏幕预览可直接保留非 Code 128 原始条码像素，避免先放大整行再长期驻留；导出不传此限制。
    val outputWidth = rasterWidthLimit
        ?.takeIf { item.format != "Code 128-B" }
        ?.let { minOf(width.coerceAtLeast(1), it.coerceAtLeast(1)) }
        ?: width
    val size = resultImageRowSize(item, style, barcode.width, barcode.height, outputWidth, density, fontScale)
    val safeWidth = size.width
    val barWidth = if (item.format == "Code 128-B") (style.barWidth.coerceIn(120f, 300f) * density).roundToInt().coerceAtMost(safeWidth)
        else safeWidth
    val label = resultLabelLayout(item, style, dark, safeWidth, density, fontScale)
    val labelPadding = (4 * density).roundToInt()
    val barHeight = if (item.format == "Code 128-B") (style.barHeight.coerceIn(30, 80) * density).roundToInt()
        else (barcode.height.toFloat() * safeWidth / barcode.width.coerceAtLeast(1)).roundToInt()
    // 结果行背景始终不透明；预览和导出都保留主题色精度，屏幕预览另以原始条码宽度控制内存。
    val result = createBitmap(safeWidth, size.height.coerceAtLeast(1), bitmapConfig)
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
    val labelText = resultImageLabel(item)
    return StaticLayout.Builder.obtain(
        labelText,
        0,
        labelText.length,
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            // 文字标签属于页面内容，不计入条码周围的浅色扫描区域。
            color = if (dark) Color.WHITE else Color.BLACK
            textSize = style.textSize.coerceIn(10f, 24f) * density * fontScale
        },
        width,
    ).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
}
