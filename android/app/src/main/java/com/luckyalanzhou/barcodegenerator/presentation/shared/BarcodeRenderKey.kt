package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.BarcodeFormatIds
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import java.security.MessageDigest
import kotlin.math.roundToInt

internal fun barcodeRenderSize(formatId: String, style: StyleSettings, density: Float): Pair<Int, Int> {
    require(density.isFinite() && density > 0)
    return if (formatId == BarcodeFormatIds.CODE_128) {
        (style.barWidth.roundToInt().coerceIn(120, 300) * density).roundToInt().coerceAtLeast(1) to
            (style.barHeight.coerceIn(30, 80) * density).roundToInt().coerceAtLeast(1)
    } else 500 to if (formatId == BarcodeFormatIds.QR_CODE) 500 else 200
}

internal fun barcodeRenderKey(item: CodeItem, pixels: Pair<Int, Int>, dark: Boolean): String {
    val raw = "barcode-bg-v3|${item.text.length}:${item.text}|${item.format.length}:${item.format}|${pixels.first}|${pixels.second}|$dark"
    return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
