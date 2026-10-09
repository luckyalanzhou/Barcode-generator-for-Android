package com.luckyalanzhou.barcodegenerator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeRenderGeometryTest {
    @Test fun sharedGeometryPreservesDensityAndFormatRules() {
        val style = StyleSettings(barWidth = 230f, barHeight = 55)
        assertEquals(460 to 110, barcodeRenderSize("code128", style, 2f))
        assertEquals(500 to 500, barcodeRenderSize("qr", style, 2f))
        assertEquals(500 to 200, barcodeRenderSize("ean13", style, 2f))
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidDensityIsRejected() { barcodeRenderSize("code128", StyleSettings(), Float.NaN) }
}
