package com.luckyalanzhou.barcodegenerator.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeValidatorTest {
    @Test
    fun acceptsUtf8QrContent() {
        assertTrue(BarcodeValidator.validate("条码😀", "QR Code").valid)
    }

    @Test
    fun rejectsUnknownFormat() {
        assertFalse(BarcodeValidator.validate("payload", "unsupported").valid)
    }

    @Test
    fun acceptsLegacyFormatIdsUsedByPersistedItems() {
        assertTrue(BarcodeValidator.validate("legacy", "CODE_128").valid)
        assertTrue(BarcodeValidator.validate("12345", "qr_code").valid)
    }

    @Test
    fun validatesCodabarPayloadAndPairedGuards() {
        assertTrue(BarcodeValidator.validate("12345", "Codabar").valid)
        assertTrue(BarcodeValidator.validate("A12345B", "Codabar").valid)
        assertFalse(BarcodeValidator.validate("A12345", "Codabar").valid)
        assertFalse(BarcodeValidator.validate("12X45", "Codabar").valid)
    }

    @Test
    fun rejectsCode39ContentLongerThanEncoderLimit() {
        assertFalse(BarcodeValidator.validate("A".repeat(81), "Code 39").valid)
    }
}
