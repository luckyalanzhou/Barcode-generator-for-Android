package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ResultActionRimTest {
    @Test fun lightAndDarkKeepVisibleOutlinesAndNonzeroHighlightsEverywhere() {
        for (background in listOf(Color.White, Color(0xFFF2F3F8), Color.Black, Color(0xFF17191D))) {
            val colors = resultActionRimColors(background)
            assertTrue(colors.outline.alpha >= .15f)
            assertTrue(colors.top.alpha > colors.side.alpha)
            assertTrue(colors.side.alpha >= .15f)
            assertTrue(colors.bottom.alpha >= .20f)
        }
        assertEquals(Color.Black.copy(alpha = .16f), resultActionRimColors(Color.White).outline)
        assertEquals(Color.White.copy(alpha = .22f), resultActionRimColors(Color.Black).outline)
    }

    @Test fun denseDisplaysStillUseThinPhysicalPixelOutlines() {
        for (density in listOf(.75f, 1f, 2f, 3f, 3.5f, 4f)) {
            assertTrue(resultActionRimStroke(density, false) in 1f..1.5f)
            assertTrue(resultActionRimStroke(density, true) >= resultActionRimStroke(density, false))
        }
    }

    @Test fun invalidDensitiesRemainFiniteAndSafe() {
        for (density in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(1f, resultActionRimStroke(density, false), 0f)
            assertEquals(1f, resultActionRimStroke(density, true), 0f)
        }
    }
}
