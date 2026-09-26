package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.preview.LanShareTiffPreviewDecoder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareTiffPreviewDecoderTest {
    @Test
    fun allowsLarge64BitPhonePhotoDimensions() {
        assertTrue(LanShareTiffPreviewDecoder.isWithinPixelBudget(8000, 6000))
        assertTrue(LanShareTiffPreviewDecoder.isWithinPixelBudget(8000, 8000, LanShareTiffPreviewDecoder.MAX_SOURCE_PIXELS_32_BIT))
        assertTrue(LanShareTiffPreviewDecoder.isWithinPixelBudget(16000, 10000))
        assertFalse(LanShareTiffPreviewDecoder.isWithinPixelBudget(16000, 16000))
    }

    @Test
    fun rejectsInvalidOrExcessivelyLargeDimensionsBeforeRasterization() {
        assertFalse(LanShareTiffPreviewDecoder.isWithinPixelBudget(0, 4000))
        assertFalse(LanShareTiffPreviewDecoder.isWithinPixelBudget(16000, 16000))
    }
}
