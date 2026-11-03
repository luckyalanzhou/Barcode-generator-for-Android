package com.luckyalanzhou.barcodegenerator.domain

data class BarcodeValidationResult(val valid: Boolean, val message: String = "")

private const val QR_NUMERIC_CAPACITY = 7_089
private const val QR_ALPHANUMERIC_CAPACITY = 4_296
private const val QR_UTF8_BYTE_CAPACITY = 2_952
private const val QR_ALPHANUMERIC_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

/** 条码格式校验规则；保留现有用户输入约束和错误文案。 */
object BarcodeValidator {
    /** 按指定格式验证内容，失败时返回可直接向用户展示的原因。 */
    fun validate(value: String, format: String): BarcodeValidationResult {
        if (value.isEmpty()) return BarcodeValidationResult(false, "内容不能为空")
        return when (format.trim().lowercase()) {
            "ean-13", "ean13", "ean_13" -> BarcodeValidationResult(UpdateSecurity.isValidEan13(value), "EAN-13 校验位或格式错误")
            "ean-8", "ean8", "ean_8" -> BarcodeValidationResult(UpdateSecurity.isValidEan8(value), "EAN-8 校验位或格式错误")
            "upc-a", "upca", "upc_a" -> BarcodeValidationResult(UpdateSecurity.isValidUpcA(value), "UPC-A 校验位或格式错误")
            "itf-14", "itf14", "itf_14", "itf" -> BarcodeValidationResult(UpdateSecurity.isValidItf14(value), "ITF-14 校验位或格式错误")
            "code 39", "code39", "code_39" -> BarcodeValidationResult(
                value.length <= 80 && value.all { it in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%" },
                "Code 39 内容超长或包含非法字符",
            )
            // Code 128-B 的编码范围是可打印 ASCII（含空格）；控制字符会让 ZXing 编码失败。
            "code 128-b", "code 128", "code_128", "code128" ->
                BarcodeValidationResult(
                    value.length <= MAX_LINEAR_BARCODE_CONTENT_LENGTH && value.all { it.code in 32..126 },
                    "Code 128-B 最多支持 $MAX_LINEAR_BARCODE_CONTENT_LENGTH 个可打印 ASCII 字符",
                )
            "qr code", "qr", "qrcode", "qr_code" -> BarcodeValidationResult(
                isEncodableQrPayload(value),
                "QR Code 内容超出编码容量",
            )
            "codabar" -> BarcodeValidationResult(
                value.length <= MAX_LINEAR_BARCODE_CONTENT_LENGTH && isValidCodabar(value),
                "Codabar 格式、字符错误或内容超过 $MAX_LINEAR_BARCODE_CONTENT_LENGTH 个字符",
            )
            else -> BarcodeValidationResult(false, "不支持的条码格式")
        }
    }

    private fun isValidCodabar(value: String): Boolean {
        val actualGuards = "ABCD"
        val alternateGuards = "TN*E"
        val first = value.first().uppercaseChar()
        val last = value.last().uppercaseChar()
        val hasActualGuard = first in actualGuards || last in actualGuards
        val hasAlternateGuard = first in alternateGuards || last in alternateGuards
        val hasPairedGuards = when {
            hasActualGuard -> first in actualGuards && last in actualGuards
            hasAlternateGuard -> first in alternateGuards && last in alternateGuards
            else -> true
        }
        val payload = if (hasActualGuard || hasAlternateGuard) value.drop(1).dropLast(1) else value
        return hasPairedGuards && payload.isNotEmpty() && payload.all { it in "0123456789-$:/.+" }
    }

    /** QR 默认低纠错级别下按 ZXing 可用的数字、字母数字或 UTF-8 字节模式限制容量。 */
    private fun isEncodableQrPayload(value: String): Boolean = when {
        value.all { it in '0'..'9' } -> value.length <= QR_NUMERIC_CAPACITY
        value.all { it in QR_ALPHANUMERIC_CHARACTERS } -> value.length <= QR_ALPHANUMERIC_CAPACITY
        else -> value.toByteArray(Charsets.UTF_8).size <= QR_UTF8_BYTE_CAPACITY
    }
}
