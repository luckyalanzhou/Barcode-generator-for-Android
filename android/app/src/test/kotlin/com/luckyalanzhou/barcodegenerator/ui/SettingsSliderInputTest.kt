package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.feature.settings.parseSliderValue
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.useStackedSlider
import org.junit.Assert.*
import org.junit.Test

class SettingsSliderInputTest {
    @Test fun acceptsIntegerEndpointsAndWhitespace() {
        assertEquals(120f, parseSliderValue("120", 120f..300f))
        assertEquals(300f, parseSliderValue(" 300 ", 120f..300f))
        assertEquals(0f, parseSliderValue("0", 0f..10f))
    }
    @Test fun rejectsOutOfRangeOrNonIntegerInput() {
        listOf("", "119", "301", "220.5", "abc", "999999999999").forEach {
            assertNull(parseSliderValue(it, 120f..300f))
        }
    }
    @Test fun normalWidthKeepsInlineLayout() {
        assertFalse(useStackedSlider(294f, 1f))
    }
    @Test fun narrowOrLargeTextUsesFullWidthTrack() {
        assertTrue(useStackedSlider(280f, 1f))
        assertTrue(useStackedSlider(360f, 1.5f))
    }
}
