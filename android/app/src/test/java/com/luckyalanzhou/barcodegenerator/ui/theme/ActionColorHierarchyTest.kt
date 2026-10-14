package com.luckyalanzhou.barcodegenerator.ui.theme

import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActionColorHierarchyTest {
    @Test
    fun bothThemesSeparateSecondarySelectedAndDisabledSurfaces() {
        listOf(LightAppColorScheme, DarkAppColorScheme).forEach { colors ->
            assertNotEquals(colors.surfaces.background, colors.controls.button)
            assertNotEquals(colors.surfaces.card, colors.controls.button)
            assertNotEquals(colors.controls.button, colors.controls.selectedContainer)
            assertNotEquals(colors.controls.button, colors.controls.disabledContainer)
            assertNotEquals(colors.controls.selectedContainer, colors.controls.disabledContainer)
        }
    }
}
