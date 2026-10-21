package com.luckyalanzhou.barcodegenerator.infrastructure.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeEncodingRoundTripTest {
    @Test
    fun qrCodeRoundTripsUnicodeUsingUtf8AndFourModuleQuietZone() {
        val text = "条码😀-42"
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 500, 500, barcodeEncodeHints(BarcodeFormat.QR_CODE))

        assertTrue("QR 静区至少为四个模块", matrix.getEnclosingRectangle()!!.let { rect ->
            rect[0] >= 4 && rect[1] >= 4 && matrix.width - rect[0] - rect[2] >= 4 && matrix.height - rect[1] - rect[3] >= 4
        })
        assertEquals(text, decode(matrix))
    }

    @Test
    fun everySupportedFormatRoundTripsRepresentativeContent() {
        val examples = listOf(
            BarcodeFormat.CODE_128 to "ABC 123",
            BarcodeFormat.QR_CODE to "文本😀",
            BarcodeFormat.CODE_39 to "ABC-39",
            BarcodeFormat.EAN_13 to "5901234123457",
            BarcodeFormat.EAN_8 to "96385074",
            BarcodeFormat.UPC_A to "036000291452",
            BarcodeFormat.ITF to "10012345000017",
            BarcodeFormat.CODABAR to "12345",
        )

        examples.forEach { (format, text) ->
            val matrix = MultiFormatWriter().encode(text, format, 500, 200, barcodeEncodeHints(format))
            assertEquals("往返解码 $format", text, decode(matrix, format))
        }
    }

    private fun decode(matrix: com.google.zxing.common.BitMatrix, format: BarcodeFormat? = null): String {
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) 0xff000000.toInt() else -1
        }
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        val reader = MultiFormatReader()
        if (format != null) reader.setHints(mapOf(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(format)))
        return reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    }
}
