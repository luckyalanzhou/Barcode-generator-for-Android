package com.luckyalanzhou.barcodegenerator.infrastructure.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.luckyalanzhou.barcodegenerator.domain.BarcodeValidator
import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.domain.MAX_LINEAR_BARCODE_CONTENT_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun qrValidatorRejectsPayloadThatExceedsZxingCapacity() {
        val text = "X".repeat(5_000)
        val encoding = runCatching {
            MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 500, 500, barcodeEncodeHints(BarcodeFormat.QR_CODE))
        }

        assertTrue("ZXing must reject text beyond QR version 40 capacity", encoding.isFailure)
        assertFalse("Domain validation must reject content the app cannot encode", BarcodeValidator.validate(text, "QR Code").valid)
        assertFalse(GenerateBarcodesUseCase().execute(listOf(text), "QR Code", emptyList()).isValid)
    }

    @Test
    fun qrValidatorAcceptsSupportedEncodingModeCapacityBoundaries() {
        val examples = listOf(
            "7".repeat(7_089),
            "X".repeat(4_296),
            "界".repeat(983),
        )

        examples.forEach { text ->
            assertTrue("Domain accepts supported QR payload length ${text.length}", BarcodeValidator.validate(text, "QR Code").valid)
            MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 500, 500, barcodeEncodeHints(BarcodeFormat.QR_CODE))
        }
    }

    @Test
    fun qrByteModeValidationMatchesZxingNearMaximumCapacity() {
        val mismatches = mutableListOf<String>()
        listOf(
            "a".repeat(2_950),
            "a".repeat(2_951),
            "a".repeat(2_952),
            "a".repeat(2_953),
            "界".repeat(984),
            "界".repeat(985),
        ).forEach { text ->
            val byteLength = text.toByteArray(Charsets.UTF_8).size
            val canEncode = runCatching {
                MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 500, 500, barcodeEncodeHints(BarcodeFormat.QR_CODE))
            }.isSuccess
            val accepted = BarcodeValidator.validate(text, "QR Code").valid
            if (canEncode != accepted) mismatches += "$byteLength bytes: ZXing=$canEncode validator=$accepted"
        }
        assertTrue("QR byte-mode validator and ZXing disagree: $mismatches", mismatches.isEmpty())
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

    @Test
    fun maximumLinearBarcodePayloadsRemainEncodable() {
        val examples = listOf(
            BarcodeFormat.CODE_128 to "A".repeat(MAX_LINEAR_BARCODE_CONTENT_LENGTH),
            BarcodeFormat.CODABAR to "A" + "1".repeat(MAX_LINEAR_BARCODE_CONTENT_LENGTH - 2) + "B",
        )

        examples.forEach { (format, text) ->
            val formatName = if (format == BarcodeFormat.CODE_128) "Code 128-B" else "Codabar"
            assertTrue("Domain accepts supported $formatName boundary", BarcodeValidator.validate(text, formatName).valid)
            MultiFormatWriter().encode(text, format, 500, 200, barcodeEncodeHints(format))
        }
    }

    @Test
    fun codabarValidationMatchesEncoderForLowercaseGuards() {
        listOf("a123b", "T123e", "a123B").forEach { text ->
            val canEncode = runCatching {
                MultiFormatWriter().encode(text, BarcodeFormat.CODABAR, 200, 80, barcodeEncodeHints(BarcodeFormat.CODABAR))
            }.isSuccess
            assertEquals("Codabar validator and ZXing must agree for '$text'", canEncode,
                BarcodeValidator.validate(text, "Codabar").valid)
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
