package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import java.security.MessageDigest

internal fun barcodeRenderKey(item: CodeItem, pixels: Pair<Int, Int>, dark: Boolean): String {
    val raw = "barcode-bg-v3|${item.text.length}:${item.text}|${item.format.length}:${item.format}|${pixels.first}|${pixels.second}|$dark"
    return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
