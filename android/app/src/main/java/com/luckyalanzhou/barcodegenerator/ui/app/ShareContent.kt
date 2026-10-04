package com.luckyalanzhou.barcodegenerator.ui.app

internal enum class ResultExportAction { Share, Save }

/** A readable filename without filesystem separators/control characters. */
internal fun shareImageFileName(label: String): String {
    val base = label.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_")
        .trim().trim('.').take(64).ifBlank { "barcode" }
    return "$base.png"
}
