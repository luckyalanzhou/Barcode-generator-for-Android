package com.luckyalanzhou.barcodegenerator.ui.app

import android.content.res.Resources
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.domain.BarcodeInputError
import com.luckyalanzhou.barcodegenerator.domain.MAX_GENERATE_INPUT_ROWS
import com.luckyalanzhou.barcodegenerator.domain.MAX_LINEAR_BARCODE_CONTENT_LENGTH

internal fun Resources.barcodeInputError(error: BarcodeInputError): String = when (error) {
    BarcodeInputError.EmptyContent -> getString(R.string.barcode_validation_empty)
    BarcodeInputError.MissingInput -> getString(R.string.input_required)
    BarcodeInputError.TooManyRows -> getString(R.string.barcode_validation_row_limit, MAX_GENERATE_INPUT_ROWS)
    BarcodeInputError.Ean13 -> getString(R.string.barcode_validation_checksum, "EAN-13")
    BarcodeInputError.Ean8 -> getString(R.string.barcode_validation_checksum, "EAN-8")
    BarcodeInputError.UpcA -> getString(R.string.barcode_validation_checksum, "UPC-A")
    BarcodeInputError.Itf14 -> getString(R.string.barcode_validation_checksum, "ITF-14")
    BarcodeInputError.Code39 -> getString(R.string.barcode_validation_code39)
    BarcodeInputError.Code128 -> getString(R.string.barcode_validation_code128, MAX_LINEAR_BARCODE_CONTENT_LENGTH)
    BarcodeInputError.QrCode -> getString(R.string.barcode_validation_qr)
    BarcodeInputError.Codabar -> getString(R.string.barcode_validation_codabar, MAX_LINEAR_BARCODE_CONTENT_LENGTH)
    BarcodeInputError.UnsupportedFormat -> getString(R.string.barcode_format_unsupported)
}
