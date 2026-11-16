package com.luckyalanzhou.barcodegenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class BarcodeFormatCatalogDescriptionTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources

    @Test
    fun everyFormatHasConciseUsageGuidance() {
        assertEquals(8, barcodeFormats.size)
        assertTrue(barcodeFormats.all { resources.getString(it.descriptionResId).length in 1..12 })
    }

    @Test
    fun guidanceExplainsCommonFormatUseCases() {
        val descriptions = barcodeFormats.associate { it.displayName to resources.getString(it.descriptionResId) }

        assertEquals("网址或较长文本", descriptions["QR Code"])
        assertEquals("零售商品条码", descriptions["EAN-13"])
        assertEquals("物流外箱条码", descriptions["ITF-14"])
    }
}
