package com.luckyalanzhou.barcodegenerator

import com.google.zxing.BarcodeFormat

/** 应用支持的条码格式目录；UI 只读取目录，不由 Activity 持有。 */
internal val barcodeFormats: List<Pair<String, BarcodeFormat>> = listOf(
    "Code 128-B" to BarcodeFormat.CODE_128,
    "QR Code" to BarcodeFormat.QR_CODE,
    "Code 39" to BarcodeFormat.CODE_39,
    "EAN-13" to BarcodeFormat.EAN_13,
    "EAN-8" to BarcodeFormat.EAN_8,
    "UPC-A" to BarcodeFormat.UPC_A,
    "ITF-14" to BarcodeFormat.ITF,
    "Codabar" to BarcodeFormat.CODABAR,
)

/** Converts legacy/imported format names back to the editor's display names. */
internal fun canonicalBarcodeFormatName(raw: String?): String {
    val value = raw?.trim().orEmpty()
    barcodeFormats.firstOrNull { it.first.equals(value, ignoreCase = true) }?.first?.let { return it }
    return when (value.lowercase()) {
        "code_128", "code128", "code 128" -> "Code 128-B"
        "qr", "qrcode", "qr_code" -> "QR Code"
        "code_39", "code39" -> "Code 39"
        "ean13", "ean_13" -> "EAN-13"
        "ean8", "ean_8" -> "EAN-8"
        "upca", "upc_a" -> "UPC-A"
        "itf14", "itf_14", "itf" -> "ITF-14"
        "codabar" -> "Codabar"
        else -> barcodeFormats.first().first
    }
}
