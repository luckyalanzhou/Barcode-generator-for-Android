package com.luckyalanzhou.barcodegenerator.ui.theme

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import androidx.compose.ui.graphics.Color
import org.junit.Test

class ActionColorHierarchyTest {
    @Test
    fun bothThemesUseNoArtificialFillForOrdinaryAndDisabledButtons() {
        listOf(LightAppColorScheme, DarkAppColorScheme).forEach { colors ->
            assertNotEquals(colors.surfaces.background, colors.controls.button)
            assertNotEquals(colors.surfaces.card, colors.controls.button)
            assertNotEquals(colors.controls.button, colors.controls.selectedContainer)
            assertEquals(Color.Transparent, colors.controls.button)
            assertEquals(Color.Transparent, colors.controls.disabledContainer)
            assertNotEquals(colors.text.primary, colors.text.disabled)
            assertNotEquals(Color.Transparent, colors.controls.accent)
            assertNotEquals(colors.controls.selectedContainer, colors.controls.disabledContainer)
        }
    }
}
