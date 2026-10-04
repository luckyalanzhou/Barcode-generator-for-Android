package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGlassMaterialTest {
    @Test
    fun bodyContrastIsBoundedAndAccentStaysSubtle() {
        val dark = tabGlassMaterial(Color(0xFF17191D), 56f)
        val light = tabGlassMaterial(Color(0xFFF5F7FC), 56f)
        assertTrue(dark.bodyTintStrength in .11f.. .15f)
        assertTrue(light.bodyTintStrength in .14f.. .17f)
        assertTrue(dark.accentTint < .01f && light.accentTint < .015f)
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
            assertTrue(small.surfaceOpacity >= .54f && large.surfaceOpacity <= .65f)
        }
    }

    @Test fun stationaryBodySeparatesFromBothThemesWithoutThickeningRim() {
        val accent = Color(0xFF007AFF)
        for (background in listOf(Color.Black, Color(0xFF17191D), Color.White, Color(0xFFF2F3F8))) {
            val material = tabGlassMaterial(background, 56f)
            val fill = tabGlassFill(background, accent, material)
            val alpha = material.surfaceOpacity
            val composite = Color(background.red + (fill.red - background.red) * alpha,
                background.green + (fill.green - background.green) * alpha,
                background.blue + (fill.blue - background.blue) * alpha)
            val difference = maxOf(kotlin.math.abs(composite.red - background.red),
                kotlin.math.abs(composite.green - background.green), kotlin.math.abs(composite.blue - background.blue))
            assertTrue("Stationary capsule body must remain visible", difference >= .04f)
            if (background.luminance() < .35f) assertTrue(composite.luminance() > background.luminance())
            else assertTrue(composite.luminance() < background.luminance())
            assertTrue(material.edgeWidthDp * .55f < .8f)
        }
    }

    @Test fun highContrastMaterialCanRetainOriginalSurfaceColor() {
        val background = Color(0xFF17191D)
        val material = tabGlassMaterial(background, 56f).copy(bodyTintStrength = 0f, accentTint = 0f)
        assertEquals(background, tabGlassFill(background, Color.Blue, material))
    }
}
