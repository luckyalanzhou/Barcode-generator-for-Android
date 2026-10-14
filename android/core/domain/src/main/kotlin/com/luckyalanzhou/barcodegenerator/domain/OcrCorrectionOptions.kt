package com.luckyalanzhou.barcodegenerator.domain

/** OCR 字符纠错选项；UI 使用展示文案和位掩码，Data 层只负责持久化掩码。 */
data class OcrCorrectionOption(val label: String, val bit: Int)

val ocrCorrectionOptions = listOf(
    OcrCorrectionOption("O → 0", OcrCorrectionMask.O_ZERO),
    OcrCorrectionOption("I → 1", OcrCorrectionMask.I_ONE),
    OcrCorrectionOption("S → 5", OcrCorrectionMask.S_FIVE),
    OcrCorrectionOption("B → 8", OcrCorrectionMask.B_EIGHT),
)
