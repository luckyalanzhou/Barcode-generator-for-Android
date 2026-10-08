package com.luckyalanzhou.barcodegenerator.domain

/** OCR 字符纠错位掩码，供设置 UI 和 OCR 服务共享，不让 OCR 依赖 Data 实现。 */
object OcrCorrectionMask {
    const val O_ZERO = 1
    const val I_ONE = 1 shl 1
    const val S_FIVE = 1 shl 2
    const val B_EIGHT = 1 shl 3
}
