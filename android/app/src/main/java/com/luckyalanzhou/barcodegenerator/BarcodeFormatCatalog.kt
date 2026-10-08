package com.luckyalanzhou.barcodegenerator

/** 应用支持的条码格式目录；UI 只读取目录，不由 Activity 持有。 */
internal data class BarcodeFormatOption(val displayName: String, val id: String)

/** Stable app-owned IDs. Persistence continues to use the existing display names. */
internal object BarcodeFormatIds {
    const val CODE_128 = "code128"
    const val QR_CODE = "qr"
    const val CODE_39 = "code39"
    const val EAN_13 = "ean13"
    const val EAN_8 = "ean8"
    const val UPC_A = "upca"
    const val ITF_14 = "itf14"
    const val CODABAR = "codabar"
}

internal val barcodeFormats: List<BarcodeFormatOption> = listOf(
    BarcodeFormatOption("Code 128-B", BarcodeFormatIds.CODE_128),
    BarcodeFormatOption("QR Code", BarcodeFormatIds.QR_CODE),
    BarcodeFormatOption("Code 39", BarcodeFormatIds.CODE_39),
    BarcodeFormatOption("EAN-13", BarcodeFormatIds.EAN_13),
    BarcodeFormatOption("EAN-8", BarcodeFormatIds.EAN_8),
    BarcodeFormatOption("UPC-A", BarcodeFormatIds.UPC_A),
    BarcodeFormatOption("ITF-14", BarcodeFormatIds.ITF_14),
    BarcodeFormatOption("Codabar", BarcodeFormatIds.CODABAR),
)

/** Converts legacy/imported format names back to the editor's display names. */
internal fun canonicalBarcodeFormatName(raw: String?): String {
    val value = raw?.trim().orEmpty()
    barcodeFormats.firstOrNull { it.displayName.equals(value, ignoreCase = true) }?.let { return it.displayName }
    val id = when (value.lowercase()) {
        "code_128", "code128", "code 128" -> BarcodeFormatIds.CODE_128
        "qr", "qrcode", "qr_code" -> BarcodeFormatIds.QR_CODE
        "code_39", "code39" -> BarcodeFormatIds.CODE_39
        "ean13", "ean_13" -> BarcodeFormatIds.EAN_13
        "ean8", "ean_8" -> BarcodeFormatIds.EAN_8
        "upca", "upc_a" -> BarcodeFormatIds.UPC_A
        "itf14", "itf_14", "itf" -> BarcodeFormatIds.ITF_14
        "codabar" -> BarcodeFormatIds.CODABAR
        else -> null
    }
    return barcodeFormats.firstOrNull { it.id == id }?.displayName ?: barcodeFormats.first().displayName
}

/** Resolves persisted names and legacy aliases to an implementation-independent ID. */
internal fun barcodeFormatId(raw: String?): String =
    barcodeFormats.firstOrNull { it.displayName == canonicalBarcodeFormatName(raw) }?.id
        ?: BarcodeFormatIds.CODE_128
