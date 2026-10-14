package com.luckyalanzhou.barcodegenerator.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TextColorSemanticsTest {
    @Test fun settingsSecondaryTextUsesTheSharedSecondaryColorInBothThemes() {
        listOf(LightAppColorScheme, DarkAppColorScheme).forEach { colors ->
            assertEquals(colors.text.secondary, colors.settingsText.secondary)
        }
    }
}
