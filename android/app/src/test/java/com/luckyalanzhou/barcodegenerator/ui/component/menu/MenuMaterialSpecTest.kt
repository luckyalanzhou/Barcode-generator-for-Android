package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class MenuMaterialSpecTest {
    @Test fun lightAndDarkStayTranslucentWhileProtectingLabels() {
        val light = menuMaterialSpec(Color.White, Color.Black, 160f, false)
        val dark = menuMaterialSpec(Color.Black, Color.White, 160f, false)
        assertTrue(light.opacity in .70f.. .74f)
        assertTrue(dark.opacity in .78f.. .82f)
        assertTrue(dark.opacity > light.opacity)
        assertTrue(dark.outline.alpha > light.outline.alpha)
        assertTrue(light.blurDp >= 12f)
    }

    @Test fun contrastUsesOpaqueUnblurredPanelWithoutSpecularEffects() {
        for (background in listOf(Color.Black, Color.White)) {
            val spec = menuMaterialSpec(background, Color.Gray, 200f, true)
            assertEquals(1f, spec.opacity, 0f)
            assertEquals(0f, spec.blurDp, 0f)
            assertEquals(0f, spec.highlight.alpha, 0f)
        }
    }

    @Test fun oversizedAndInvalidHeightCannotProduceInvalidMaterial() {
        for (height in listOf(-100f, 10000f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val spec = menuMaterialSpec(Color.White, Color.Black, height, false)
            assertTrue(spec.opacity in .70f.. .74f)
            assertTrue(spec.blurDp in 12f..14f)
        }
    }
}
