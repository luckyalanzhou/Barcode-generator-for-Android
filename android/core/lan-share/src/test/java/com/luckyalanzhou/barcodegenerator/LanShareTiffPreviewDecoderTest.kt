package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.preview.LanShareTiffPreviewDecoder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareTiffPreviewDecoderTest {
    @Test
    fun allowsLarge64BitPhonePhotoDimensions() {
        assertTrue(LanShareTiffPreviewDecoder.isWithinPixelBudget(8000, 6000))
        assertFalse(LanShareTiffPreviewDecoder.isWithinPixelBudget(8000, 6000, 24_000_000L))
    }

    @Test
    fun rejectsInvalidOrExcessivelyLargeDimensionsBeforeRasterization() {
        assertFalse(LanShareTiffPreviewDecoder.isWithinPixelBudget(0, 4000))
        assertFalse(LanShareTiffPreviewDecoder.isWithinPixelBudget(12000, 8000))
    }
}
