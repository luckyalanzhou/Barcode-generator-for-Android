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
