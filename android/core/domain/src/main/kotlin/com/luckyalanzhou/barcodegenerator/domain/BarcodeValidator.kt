package com.luckyalanzhou.barcodegenerator.domain

data class BarcodeValidationResult(val valid: Boolean, val message: String = "")

/** 条码格式校验规则；保留现有用户输入约束和错误文案。 */
object BarcodeValidator {
    fun validate(value: String, format: String): BarcodeValidationResult {
        if (value.isEmpty()) return BarcodeValidationResult(false, "内容不能为空")
        return when (format) {
            "EAN-13" -> BarcodeValidationResult(UpdateSecurity.isValidEan13(value), "EAN-13 校验位或格式错误")
            "EAN-8" -> BarcodeValidationResult(UpdateSecurity.isValidEan8(value), "EAN-8 校验位或格式错误")
            "UPC-A" -> BarcodeValidationResult(UpdateSecurity.isValidUpcA(value), "UPC-A 校验位或格式错误")
            "ITF-14" -> BarcodeValidationResult(UpdateSecurity.isValidItf14(value), "ITF-14 校验位或格式错误")
            "Code 39" -> BarcodeValidationResult(value.all { it in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%" }, "Code 39 包含非法字符")
            // Code 128-B 的编码范围是可打印 ASCII（含空格）；控制字符会让 ZXing 编码失败。
            "Code 128-B" -> BarcodeValidationResult(value.all { it.code in 32..127 }, "Code 128-B 仅支持可打印 ASCII 字符")
            else -> BarcodeValidationResult(true)
        }
    }
}
