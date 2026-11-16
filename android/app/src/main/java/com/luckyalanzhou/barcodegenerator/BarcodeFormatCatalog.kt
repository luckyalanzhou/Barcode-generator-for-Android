package com.luckyalanzhou.barcodegenerator

/** 应用支持的条码格式目录；UI 只读取目录，不由 Activity 持有。 */
internal data class BarcodeFormatOption(
    val displayName: String,
    val id: String,
    @androidx.annotation.StringRes val descriptionResId: Int,
)

/** 稳定的应用内格式 ID；持久化数据仍使用原有显示名称。 */
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
    BarcodeFormatOption("Code 128-B", BarcodeFormatIds.CODE_128, R.string.format_letters_numbers_symbols),
    BarcodeFormatOption("QR Code", BarcodeFormatIds.QR_CODE, R.string.format_urls_long_text),
    BarcodeFormatOption("Code 39", BarcodeFormatIds.CODE_39, R.string.format_uppercase_numbers_symbols),
    BarcodeFormatOption("EAN-13", BarcodeFormatIds.EAN_13, R.string.format_retail_products),
    BarcodeFormatOption("EAN-8", BarcodeFormatIds.EAN_8, R.string.format_small_packages),
    BarcodeFormatOption("UPC-A", BarcodeFormatIds.UPC_A, R.string.format_retail_products),
    BarcodeFormatOption("ITF-14", BarcodeFormatIds.ITF_14, R.string.format_shipping_cartons),
    BarcodeFormatOption("Codabar", BarcodeFormatIds.CODABAR, R.string.format_numbers_common_symbols),
)

/** 查找显示名称或旧版别名；预览入口用 null 区分不支持的值。 */
internal fun barcodeFormatOption(raw: String?): BarcodeFormatOption? {
    val value = raw?.trim().orEmpty()
    barcodeFormats.firstOrNull { it.displayName.equals(value, ignoreCase = true) }?.let { return it }
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
    return barcodeFormats.firstOrNull { it.id == id }
}

/** 将旧版或导入的格式名称转换为编辑器使用的显示名称。 */
internal fun canonicalBarcodeFormatName(raw: String?): String =
    barcodeFormatOption(raw)?.displayName ?: barcodeFormats.first().displayName

/** 将已持久化的名称和旧别名解析为不依赖具体实现的 ID。 */
internal fun barcodeFormatId(raw: String?): String =
    barcodeFormatOption(raw)?.id ?: BarcodeFormatIds.CODE_128
