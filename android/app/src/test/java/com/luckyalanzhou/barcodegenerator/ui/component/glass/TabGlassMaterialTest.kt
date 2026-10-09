package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGlassMaterialTest {
    @Test fun circularActionsRemainVisibleAtRestAcrossBothThemes() {
        for (background in listOf(Color.Black, Color(0xFF17191D), Color.White, Color(0xFFF2F3F8))) {
            val material = resultActionGlassMaterial(background)
            val fill = tabGlassFill(background, Color(0xFF007AFF), material)
            val difference = maxOf(kotlin.math.abs(fill.red - background.red),
                kotlin.math.abs(fill.green - background.green), kotlin.math.abs(fill.blue - background.blue)) * material.surfaceOpacity
            assertTrue("The surface must not add a gray body tint: background=$background fill=$fill difference=$difference",
                difference <= .005f)
            assertTrue(material.rimLight >= .30f && material.innerShadow in .04f.. .07f)
            assertTrue(material.accentTint < .01f)
            assertTrue(material.rimLight <= .75f)
            assertTrue(tabGlassEdgeWidthPx(material, 3f) <= 1.5f)
        }
    }

    @Test fun circularActionsKeepAReadableStaticRimAcrossThemes() {
        val light = resultActionGlassMaterial(Color(0xFFF2F2F7))
        val dark = resultActionGlassMaterial(Color.Black)
        assertEquals(.09f, tabGlassOutlineColor(Color(0xFFF2F2F7), light).alpha, .001f)
        assertEquals(.10f, tabGlassOutlineColor(Color.Black, dark).alpha, .003f)
        assertTrue(light.rimLight >= .58f && dark.rimLight >= .40f)
    }

    @Test
    fun fixedNeutralBodyTintIsRemovedAndAccentStaysSubtle() {
        val dark = tabGlassMaterial(Color(0xFF17191D), 56f)
        val light = tabGlassMaterial(Color(0xFFF5F7FC), 56f)
        assertTrue(dark.accentTint < .01f && light.accentTint < .015f)
        for (background in listOf(Color(0xFF17191D), Color(0xFFF5F7FC))) {
            val material = tabGlassMaterial(background, 56f).copy(accentTint = 0f)
            assertEquals(background, tabGlassFill(background, Color(0xFF007AFF), material))
        }
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
            assertTrue(large.refractionDp <= 4.3f)
            assertTrue(large.edgeWidthDp <= 1.5f)
            assertTrue(small.surfaceOpacity >= .46f && large.surfaceOpacity <= .58f)
        }
    }

    @Test fun stationaryFillKeepsBackdropColorWithoutAddingNeutralGray() {
        val accent = Color(0xFF007AFF)
        for (background in listOf(Color.Black, Color(0xFF17191D), Color.White, Color(0xFFF2F3F8))) {
            val material = tabGlassMaterial(background, 56f)
            val untinted = tabGlassFill(background, accent, material.copy(accentTint = 0f))
            assertEquals(background, untinted)
            assertTrue(material.accentTint <= .015f)
            assertTrue(material.edgeWidthDp * .55f < .8f)
        }
    }

    @Test fun highContrastMaterialCanRetainOriginalSurfaceColor() {
        val background = Color(0xFF17191D)
        val material = tabGlassMaterial(background, 56f).copy(accentTint = 0f)
        assertEquals(background, tabGlassFill(background, Color.Blue, material))
    }

    @Test fun restingBackdropHasSmallLensButRestingForegroundRemainsUntouched() {
        val rest = tabGlassFrame(1200f, 168f, 3f, 4, 0f, 0f, 0f, 0f)
        assertEquals(.8f, tabBackdropRefractionDp(rest), .001f)
        assertEquals(0f, rest.refractionPx, 0f)
        assertEquals(0f, tabForegroundDisplacement(rest, 0f), 0f)
        for (step in 0..100) {
            val frame = tabGlassFrame(1200f, 168f, 3f, 4, 1.5f, step / 100f, 0f, 1f, refractionDp = 4.4f)
            assertTrue(tabBackdropRefractionDp(frame) in .8f..3.0f)
        }
        assertEquals(0f, tabBackdropRefractionDp(rest.copy(density = Float.NaN)), 0f)
    }

    @Test fun fallbackKeepsControlTransparencyWithoutReducingMenuReadability() {
        for (opacity in listOf(.50f, .54f, .60f)) {
            assertEquals(opacity, glassFallbackOpacity(opacity, smallControl = true, opaque = false), 0f)
            assertEquals(.92f, glassFallbackOpacity(opacity, smallControl = false, opaque = false), 0f)
            assertEquals(1f, glassFallbackOpacity(opacity, smallControl = true, opaque = true), 0f)
        }
        assertEquals(0f, glassFallbackOpacity(-1f, smallControl = true, opaque = false), 0f)
        assertEquals(1f, glassFallbackOpacity(2f, smallControl = false, opaque = false), 0f)
    }

    @Test fun bevelStaysThinInPhysicalPixelsAcrossDisplayDensities() {
        val material = tabGlassMaterial(Color.White, 56f)
        for (density in listOf(1f, 2f, 3f, 4f)) {
            assertTrue(tabGlassEdgeWidthPx(material, density) in 1f..1.5f)
        }
    }

    @Test fun outlineSeparatesLightAndDarkWithoutAccentGlow() {
        val light = tabGlassMaterial(Color.White, 56f)
        val dark = tabGlassMaterial(Color.Black, 56f)
        val lightOutline = tabGlassOutlineColor(Color.White, light)
        val darkOutline = tabGlassOutlineColor(Color.Black, dark)
        assertEquals(0f, lightOutline.red, 0f)
        assertEquals(1f, darkOutline.red, 0f)
        assertTrue(lightOutline.alpha in .07f.. .09f)
        assertTrue(darkOutline.alpha in .08f.. .10f)
        assertTrue(dark.rimLight in .32f.. .37f)
    }
}
