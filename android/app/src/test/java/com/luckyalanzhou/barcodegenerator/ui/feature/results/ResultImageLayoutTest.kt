package com.luckyalanzhou.barcodegenerator.ui.feature.results

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import org.junit.Assert.*
import org.junit.Test

class ResultImageLayoutTest {
    private val item = CodeItem(1, "ABC-123", "Code 128-B")
    @Test fun exportAndScreenLabelsIncludeTheSameOptionalFormat() {
        assertEquals("ABC-123", resultImageLabel(item, false))
        assertEquals("ABC-123 · Code 128-B", resultImageLabel(item, true))
    }
    @Test fun spacingIsDensityScaledAndLimitedToTheSettingsRange() {
        assertEquals(14, resultImageSpacing(4, 3.5f))
        assertEquals(0, resultImageSpacing(-1, 3.5f))
        assertEquals(35, resultImageSpacing(99, 3.5f))
    }

    @Test fun nonCode128RowsCanBeSizedWithoutAllocatingBitmaps() {
        val qrSize = resultImageRowSize(
            CodeItem(2, "QR-CONTENT", "QR Code"),
            StyleSettings(),
            width = 900,
            density = 1f,
            fontScale = 1f,
        )
        val linearSize = resultImageRowSize(
            CodeItem(3, "EAN-CONTENT", "EAN-13"),
            StyleSettings(),
            width = 900,
            density = 1f,
            fontScale = 1f,
        )

        assertEquals(ResultImageRowSize(900, 900), qrSize)
        assertEquals(ResultImageRowSize(900, 360), linearSize)
    }
}
