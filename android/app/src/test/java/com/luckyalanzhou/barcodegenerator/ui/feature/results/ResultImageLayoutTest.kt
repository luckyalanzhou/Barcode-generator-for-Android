package com.luckyalanzhou.barcodegenerator.ui.feature.results

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
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
}
