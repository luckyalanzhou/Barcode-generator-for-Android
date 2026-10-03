package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGlassMaterialTest {
    @Test
    fun darkBackgroundUsesLessWhiteAndKeepsTintSubtle() {
        val dark = tabGlassMaterial(Color(0xFF17191D), 56f)
        val light = tabGlassMaterial(Color(0xFFF5F7FC), 56f)
        assertTrue(dark.whiteLift < light.whiteLift)
        assertTrue(dark.whiteLift < .05f)
        assertTrue(dark.accentTint < .03f && light.accentTint < .04f)
        assertTrue(dark.rimLight > dark.whiteLift)
    }

    @Test
    fun sizeAdaptationIsBoundedOutsideNormalTabHeights() {
        for (background in listOf(Color.Black, Color.White)) {
            val small = tabGlassMaterial(background, 48f)
            val large = tabGlassMaterial(background, 84f)
            assertEquals(small, tabGlassMaterial(background, 0f))
            assertEquals(large, tabGlassMaterial(background, 1000f))
            assertTrue(large.refractionDp > small.refractionDp)
            assertTrue(large.edgeWidthDp > small.edgeWidthDp)
            assertTrue(large.refractionDp <= 3f)
            assertTrue(large.edgeWidthDp <= 1.5f)
            assertTrue(small.surfaceOpacity > 0f && large.surfaceOpacity < .5f)
        }
    }
}
