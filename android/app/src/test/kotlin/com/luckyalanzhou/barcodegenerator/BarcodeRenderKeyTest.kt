package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.BarcodeFormatIds
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.presentation.shared.barcodeRenderKey
import com.luckyalanzhou.barcodegenerator.presentation.shared.barcodeRenderSize
import org.junit.Assert.*
import org.junit.Test

class BarcodeRenderKeyTest {
    private val item = CodeItem(1, "ABC|123", "Code 128-B")

    @Test
    fun actualPixelDensityAndRoundedWidthDetermineCacheIdentity() {
        val style = StyleSettings()
        val one = barcodeRenderSize(BarcodeFormatIds.CODE_128, style, 1f)
        val three = barcodeRenderSize(BarcodeFormatIds.CODE_128, style, 3f)
        assertNotEquals(barcodeRenderKey(item, one, false), barcodeRenderKey(item, three, false))
        assertEquals(one.first * 3, three.first)
    }

    @Test
    fun displayOnlyLabelSettingsDoNotInvalidateEncodedBarcode() {
        val style = StyleSettings()
        val changed = style.copy(textSize = 24f, showFormat = !style.showFormat)
        assertEquals(barcodeRenderSize(BarcodeFormatIds.CODE_128, style, 2f), barcodeRenderSize(BarcodeFormatIds.CODE_128, changed, 2f))
        val pixels = barcodeRenderSize(BarcodeFormatIds.CODE_128, style, 2f)
        assertNotEquals(barcodeRenderKey(item, pixels, false), barcodeRenderKey(item, pixels, true))
        assertEquals(barcodeRenderKey(item, pixels, false), barcodeRenderKey(item.copy(id = 900), pixels, false))
    }
}
