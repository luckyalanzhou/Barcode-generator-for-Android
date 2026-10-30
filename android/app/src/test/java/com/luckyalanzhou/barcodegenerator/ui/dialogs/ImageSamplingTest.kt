package com.luckyalanzhou.barcodegenerator.ui.dialogs

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSamplingTest {
    @Test
    fun sampleSizeKeepsSmallImagesAtOriginalResolution() {
        assertEquals(1, calculateInSampleSize(1600, 1200, OCR_RECOGNITION_MAX_EDGE))
    }

    @Test
    fun sampleSizeBoundsLargeLandscapeAndPortraitImages() {
        assertEquals(4, calculateInSampleSize(8000, 6000, OCR_RECOGNITION_MAX_EDGE))
        assertEquals(2, calculateInSampleSize(3024, 4032, BARCODE_RECOGNITION_MAX_EDGE))
    }

    @Test
    fun invalidDimensionsDoNotProduceAnInvalidSampleSize() {
        assertEquals(1, calculateInSampleSize(0, 4000, BARCODE_RECOGNITION_MAX_EDGE))
        assertEquals(1, calculateInSampleSize(4000, 4000, 0))
    }

    @Test
    fun exifTransformIncludesRotationsAndMirroring() {
        assertEquals(ExifBitmapTransform(), exifBitmapTransform(1))
        assertEquals(ExifBitmapTransform(flipHorizontally = true), exifBitmapTransform(2))
        assertEquals(ExifBitmapTransform(rotationDegrees = 90), exifBitmapTransform(6))
        assertEquals(ExifBitmapTransform(rotationDegrees = 270, flipHorizontally = true), exifBitmapTransform(7))
    }
}
