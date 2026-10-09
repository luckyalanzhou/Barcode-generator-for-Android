package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonOutlineVisibilityTest {
    @Test fun neutralOutlineRemainsDistinctOnPageAndCardsInBothThemes() {
        for (colors in listOf(LightAppColorScheme, DarkAppColorScheme)) {
            for (surface in listOf(colors.surfaces.background, colors.surfaces.card)) {
                val background = surface.luminance()
                val edge = colors.borders.button.compositeOver(surface).luminance()
                val contrast = (maxOf(edge, background) + .05f) / (minOf(edge, background) + .05f)
                assertTrue("按钮边缘与背景需要可分辨，实际对比度=$contrast", contrast >= 1.3f)
            }
        }
    }
    @Test fun darkEdgesLightenAndLightEdgesDarkenTheirBackground() {
        val light = LightAppColorScheme
        val dark = DarkAppColorScheme
        assertTrue(light.borders.button.compositeOver(light.surfaces.background).luminance() < light.surfaces.background.luminance())
        assertTrue(dark.borders.button.compositeOver(dark.surfaces.background).luminance() > dark.surfaces.background.luminance())
    }
}
