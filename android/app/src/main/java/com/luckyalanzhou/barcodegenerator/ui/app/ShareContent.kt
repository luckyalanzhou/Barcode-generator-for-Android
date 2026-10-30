package com.luckyalanzhou.barcodegenerator.ui.app

/** 可读的文件名，不包含路径分隔符或控制字符。 */
internal fun shareImageFileName(label: String): String {
    val base = label.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_")
        .trim().trim('.').take(64).ifBlank { "barcode" }
    return "$base.png"
}
