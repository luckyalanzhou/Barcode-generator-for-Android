package com.luckyalanzhou.barcodegenerator

import androidx.exifinterface.media.ExifInterface
import com.luckyalanzhou.barcodegenerator.data.preview.ExifOrientationTransform
import com.luckyalanzhou.barcodegenerator.data.preview.exifOrientationTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanShareExifOrientationTest {
    @Test
    fun mapsEveryExifOrientationIncludingMirroredVariants() {
        assertNull(exifOrientationTransform(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(ExifOrientationTransform(0f, -1f, 1f), exifOrientationTransform(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(ExifOrientationTransform(180f, 1f, 1f), exifOrientationTransform(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(ExifOrientationTransform(0f, 1f, -1f), exifOrientationTransform(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(ExifOrientationTransform(90f, -1f, 1f), exifOrientationTransform(ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(ExifOrientationTransform(90f, 1f, 1f), exifOrientationTransform(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(ExifOrientationTransform(270f, -1f, 1f), exifOrientationTransform(ExifInterface.ORIENTATION_TRANSVERSE))
        assertEquals(ExifOrientationTransform(270f, 1f, 1f), exifOrientationTransform(ExifInterface.ORIENTATION_ROTATE_270))
        assertNull(exifOrientationTransform(0))
    }
}
