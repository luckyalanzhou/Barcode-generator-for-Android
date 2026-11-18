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
            assertTrue(material.rimLight >= .30f && material.innerShadow in .075f.. .15f)
            assertTrue(material.surfaceOpacity in .18f.. .22f)
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

    @Test fun strengthChangesOnlyOpacityAcrossEveryTenPercentStep() {
        for (background in listOf(Color.Black, Color(0xFF17191D), Color.White, Color(0xFFF2F3F8))) {
            val tabBaseline = tabGlassMaterial(background, 64f)
            val actionBaseline = resultActionGlassMaterial(background)
            var previousTab = -1f
            var previousAction = -1f
            var previousTabOutline = -1f
            var previousActionOutline = -1f
            for (strength in 0..100 step 10) {
                val tab = tabGlassMaterial(background, 64f, strength)
                val action = resultActionGlassMaterial(background, strength)
                assertTrue("tab opacity must increase: $background at $strength%", tab.surfaceOpacity > previousTab)
                assertTrue("action opacity must increase: $background at $strength%", action.surfaceOpacity > previousAction)
                assertTrue("tab outline opacity must increase: $background at $strength%", tab.outlineOpacity > previousTabOutline)
                assertTrue("action outline opacity must increase: $background at $strength%", action.outlineOpacity > previousActionOutline)
                assertTrue(tab.surfaceOpacity in 0f..1f && tab.outlineOpacity in 0f..1f)
                assertTrue(action.surfaceOpacity in 0f..1f && action.outlineOpacity in 0f..1f)
                assertEquals(tabBaseline.accentTint, tab.accentTint, 0f)
                assertEquals(tabBaseline.rimLight, tab.rimLight, 0f)
                assertEquals(tabBaseline.innerShadow, tab.innerShadow, 0f)
                assertEquals(tabBaseline.edgeWidthDp, tab.edgeWidthDp, 0f)
                assertEquals(tabBaseline.refractionDp, tab.refractionDp, 0f)
                assertEquals(actionBaseline.rimLight, action.rimLight, 0f)
                assertEquals(actionBaseline.innerShadow, action.innerShadow, 0f)
                assertEquals(actionBaseline.edgeWidthDp, action.edgeWidthDp, 0f)
                assertEquals(actionBaseline.refractionDp, action.refractionDp, 0f)
                if (strength == 0) {
                    assertEquals(tabBaseline.surfaceOpacity * .75f, tab.surfaceOpacity, .001f)
                    assertEquals(actionBaseline.surfaceOpacity * .75f, action.surfaceOpacity, .001f)
                    assertEquals(tabBaseline.outlineOpacity * .75f, tab.outlineOpacity, .001f)
                    assertEquals(actionBaseline.outlineOpacity * .75f, action.outlineOpacity, .001f)
                }
                if (strength == 50) {
                    assertEquals(tabBaseline.surfaceOpacity, tab.surfaceOpacity, 0f)
                    assertEquals(actionBaseline.surfaceOpacity, action.surfaceOpacity, 0f)
                    assertEquals(tabBaseline.outlineOpacity, tab.outlineOpacity, 0f)
                    assertEquals(actionBaseline.outlineOpacity, action.outlineOpacity, 0f)
                }
                previousTab = tab.surfaceOpacity
                previousAction = action.surfaceOpacity
                previousTabOutline = tab.outlineOpacity
                previousActionOutline = action.outlineOpacity
            }
        }
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

    @Test fun restingBackdropHasSmallLensAndForegroundRemainsUntouched() {
        val rest = tabGlassFrame(1200f, 168f, 3f, 4, 0f, 0f, 0f, 0f)
        assertEquals(.8f, tabBackdropRefractionDp(rest), .001f)
        assertEquals(0f, rest.refractionPx, 0f)
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

    @Test fun fallbackStrengthMapsControlsAndLargeSurfaceFloorAtEveryStep() {
        var previousControl = -1f
        var previousLargeSurface = -1f
        for (strength in 0..100 step 10) {
            val control = glassFallbackOpacity(.60f, smallControl = true, opaque = false, strengthPercent = strength)
            val largeSurface = glassFallbackOpacity(.60f, smallControl = false, opaque = false, strengthPercent = strength)
            assertTrue(control > previousControl)
            assertTrue(largeSurface > previousLargeSurface)
            if (strength == 50) {
                assertEquals(.60f, control, 0f)
                assertEquals(.92f, largeSurface, 0f)
            }
            if (strength == 0) assertEquals(.45f, control, .001f)
            previousControl = control
            previousLargeSurface = largeSurface
        }
        assertEquals(1f, glassFallbackOpacity(.60f, false, true, 0), 0f)
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
