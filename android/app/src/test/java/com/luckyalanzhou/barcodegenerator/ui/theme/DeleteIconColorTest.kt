package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteIconColorTest {
    @Test
    fun bothThemesUseTheSameSoftRed() {
        val color = LightAppColorScheme.content.deleteIcon
        assertEquals(color, DarkAppColorScheme.content.deleteIcon)
        assertTrue(color.red > color.green)
        assertEquals(color.green, color.blue, 0f)
    }

    @Test
    fun deleteIconsRemainDistinctFromDestructiveText() {
        listOf(LightAppColorScheme, DarkAppColorScheme).forEach { colors ->
            assertNotEquals(colors.text.destructive, colors.content.deleteIcon)
        }
    }

    @Test
    fun iconsHaveAtLeastThreeToOneContrastOnCards() {
        listOf(LightAppColorScheme, DarkAppColorScheme).forEach { colors ->
            val icon = colors.content.deleteIcon.luminance()
            val card = colors.surfaces.card.luminance()
            val contrast = (maxOf(icon, card) + .05f) / (minOf(icon, card) + .05f)
            assertTrue("Delete icon contrast: $contrast", contrast >= 3f)
        }
    }
}
