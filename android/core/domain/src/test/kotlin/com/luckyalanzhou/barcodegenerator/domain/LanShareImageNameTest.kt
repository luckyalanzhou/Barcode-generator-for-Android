package com.luckyalanzhou.barcodegenerator.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareImageNameTest {
    @Test
    fun recognizesAndroidAndTiffPreviewTypes() {
        listOf("photo.bmp", "photo.HEIC", "photo.heif", "photo.avif", "scan.tif", "scan.TIFF").forEach {
            assertTrue(it, isLanShareImageName(it))
        }
        assertTrue(isLanShareTiffName("scan.tif"))
        assertTrue(isLanShareTiffName("scan.TIFF"))
    }

    @Test
    fun previewPayloadLimitFitsWithinPreviewCache() {
        assertTrue(LAN_SHARE_PREVIEW_MAX_FILE_BYTES <= LAN_SHARE_PREVIEW_CACHE_MAX_BYTES)
    }
}
