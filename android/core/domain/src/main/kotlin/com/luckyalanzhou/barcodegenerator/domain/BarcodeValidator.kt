package com.luckyalanzhou.barcodegenerator.domain

enum class BarcodeInputError {
    EmptyContent,
    MissingInput,
    TooManyRows,
    Ean13,
    Ean8,
    UpcA,
    Itf14,
    Code39,
    Code128,
    QrCode,
    Codabar,
    UnsupportedFormat,
}

data class BarcodeValidationResult(val error: BarcodeInputError?) {
    val valid: Boolean get() = error == null
}

private fun validationResult(valid: Boolean, failure: BarcodeInputError) =
    BarcodeValidationResult(failure.takeUnless { valid })

private const val QR_NUMERIC_CAPACITY = 7_089
private const val QR_ALPHANUMERIC_CAPACITY = 4_296
private const val QR_UTF8_BYTE_CAPACITY = 2_952
private const val QR_ALPHANUMERIC_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

/** 条码格式校验规则；保留现有用户输入约束和校验原因。 */
object BarcodeValidator {
    /** 按指定格式验证内容，失败时返回稳定的错误类型。 */
    fun validate(value: String, format: String): BarcodeValidationResult {
        if (value.isEmpty()) return BarcodeValidationResult(BarcodeInputError.EmptyContent)
        return when (format.trim().lowercase()) {
            "ean-13", "ean13", "ean_13" -> validationResult(UpdateSecurity.isValidEan13(value), BarcodeInputError.Ean13)
            "ean-8", "ean8", "ean_8" -> validationResult(UpdateSecurity.isValidEan8(value), BarcodeInputError.Ean8)
            "upc-a", "upca", "upc_a" -> validationResult(UpdateSecurity.isValidUpcA(value), BarcodeInputError.UpcA)
            "itf-14", "itf14", "itf_14", "itf" -> validationResult(UpdateSecurity.isValidItf14(value), BarcodeInputError.Itf14)
            "code 39", "code39", "code_39" -> validationResult(
                value.length <= 80 && value.all { it in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%" },
                BarcodeInputError.Code39,
            )
            // Code 128-B 的编码范围是可打印 ASCII（含空格）；控制字符会让 ZXing 编码失败。
            "code 128-b", "code 128", "code_128", "code128" ->
                validationResult(
                    value.length <= MAX_LINEAR_BARCODE_CONTENT_LENGTH && value.all { it.code in 32..126 },
                    BarcodeInputError.Code128,
                )
            "qr code", "qr", "qrcode", "qr_code" -> validationResult(
                isEncodableQrPayload(value),
                BarcodeInputError.QrCode,
            )
            "codabar" -> validationResult(
                value.length <= MAX_LINEAR_BARCODE_CONTENT_LENGTH && isValidCodabar(value),
                BarcodeInputError.Codabar,
            )
            else -> BarcodeValidationResult(BarcodeInputError.UnsupportedFormat)
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
