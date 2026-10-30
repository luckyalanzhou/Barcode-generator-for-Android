package com.luckyalanzhou.barcodegenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeFormatCatalogDescriptionTest {
    @Test
    fun everyFormatHasConciseUsageGuidance() {
        assertEquals(8, barcodeFormats.size)
        assertTrue(barcodeFormats.all { it.description.isNotBlank() && it.description.length <= 12 })
    }

    @Test
    fun guidanceExplainsCommonFormatUseCases() {
        val descriptions = barcodeFormats.associate { it.displayName to it.description }

        assertEquals("网址或较长文本", descriptions["QR Code"])
        assertEquals("零售商品条码", descriptions["EAN-13"])
        assertEquals("物流外箱条码", descriptions["ITF-14"])
    }
}
