package com.luckyalanzhou.barcodegenerator.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareImageNameTest {
    @Test
    fun recognizesSystemSupportedAndroidImageTypes() {
        listOf("photo.bmp", "photo.HEIC", "photo.heif", "photo.avif").forEach {
            assertTrue(it, isLanShareImageName(it))
        }
    }

    @Test
    fun leavesTiffAsDownloadableAttachmentOnAndroid() {
        assertFalse(isLanShareImageName("scan.tif"))
        assertFalse(isLanShareImageName("scan.tiff"))
    }
}
