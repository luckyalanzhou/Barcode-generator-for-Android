package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.preview.LanShareImagePreviewDecoder
import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareImagePreviewDecoderTest {
    @Test
    fun largeImagesAreSampledBeforeBitmapDecode() {
        assertEquals(1, LanShareImagePreviewDecoder.sampleSize(4000, 3000))
        assertEquals(2, LanShareImagePreviewDecoder.sampleSize(8000, 6000))
        assertEquals(4, LanShareImagePreviewDecoder.sampleSize(12000, 9000))
        assertEquals(1, LanShareImagePreviewDecoder.sampleSize(0, 6000))
    }

    @Test
    fun invalidEdgeDoesNotCauseUnboundedSampling() {
        assertEquals(1, LanShareImagePreviewDecoder.sampleSize(12000, 9000, maxEdge = 0))
    }
}
